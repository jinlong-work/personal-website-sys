# 个人网站后端

## 代码结构与持久化

后端采用 Maven 多模块工程，登录和账号资料归入 admin，通用框架与共享代码独立。持久化使用 MyBatis-Plus，Service 接口和实现分离。详细目录、依赖关系与开发约定见 [架构说明](docs/architecture.md)。

```text
personal-website-sys/                 父工程，统一版本与依赖管理
├── personal-website-api/             启动入口与两个应用配置文件
├── personal-website-admin/           管理员登录、账号资料、用户Mapper、鉴权
├── personal-website-common/          公共响应、常量、异常、工具
├── personal-website-framework/       通用Web、Redis、日志、密码编码、异常处理
├── personal-website-modules/         后续业务模块聚合
│   ├── personal-website-ai/          AI模块预留
│   └── personal-website-map/         地图模块预留
├── sql/                             数据库脚本
└── docs/                            接口、架构与日志说明
```

无需改表，接口路径保持不变，登录协议已迁移为 Bearer JWT，前后端需同步更新。请在 IDE 中以根目录 `pom.xml` 重新加载 Maven，使用 `personal-website-api` 模块的启动类运行。按要求未运行构建或测试。

### 启动与打包

在后端根目录先安装模块依赖，再启动 API 模块（以下命令供自行执行，本次未执行）：

```powershell
./mvnw.cmd -DskipTests clean install
./mvnw.cmd -pl personal-website-api spring-boot:run
```

也可以在 IDE 中直接运行 API 模块的 `com.jinlong.personalwebsitesys.PersonalWebsiteSysApplication`，工作目录设为后端根目录。父工程和业务模块不独立启动。

仅打包启动模块及其依赖：

```powershell
./mvnw.cmd -pl personal-website-api -am -DskipTests clean package
java -jar personal-website-api/target/personal-website-api-0.0.1-SNAPSHOT.jar
```

## PostgreSQL 连接

已添加 MyBatis-Plus 和 PostgreSQL 驱动，通过 Spring 数据源与 Mapper 访问数据库。

- 主机：`39.105.91.141`
- 端口：`5432`
- 数据库：`postgres`（截图中的初始数据库）
- 用户：`postgres`

配置统一位于 `personal-website-api/src/main/resources`，仅使用两个应用配置文件：

- `application.yml`：公共配置，包括服务端口、Token 时效、连接池、MyBatis-Plus 与默认环境选择。
- `application-dev.yml`：本机开发环境配置，包含具体数据库和Redis地址、账号、密码与随机 JWT 密钥，已加入 Git 忽略，不提交到远程仓库。

默认启用 `dev`，启动 API 模块即可加载开发配置。换机器开发时，需要自行创建本机的 `application-dev.yml` 并填入数据库连接配置。dev 文件已迁移到启动模块，Git 忽略规则同时覆盖了新路径。

生产环境请设置 `SPRING_PROFILES_ACTIVE=prod`，通过 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`JWT_SECRET` 与 `AUTH_ALLOWED_ORIGINS` 提供实际配置，无需再添加其他应用配置文件。Git 忽略不影响 Maven 的资源打包，本机打包的 JAR 仍可能包含 dev 配置，发布时需在不包含本机 dev 文件的干净检出中构建。

连接配置不会自动建表或执行初始化 SQL。请手动执行 `sql/001_create_sys_user.sql` 创建账号表；数据库密码不是后台页面的登录密码。

按要求未运行连接测试，实际网络连通性和账号权限需要自行验证。

## Redis 连接与登录缓存

已添加 Spring Boot Redis Starter，默认使用 Lettuce 与 `StringRedisTemplate`，连接配置继续放在现有两个应用配置文件中，不新增应用配置文件。

- dev 的远程地址：`39.105.91.141:6379`，单机、Password认证。
- 数据库：截图未指定，使用 Redis 数据库 `0`，可在 dev 修改。
- Redis密码仅放在本机 dev 配置；`SPRING_DATA_REDIS_PASSWORD` 可覆盖，未写入公共配置或接口文档。
- 连接与操作超时均为5秒，配置在公共 `spring.data.redis` 下。
- 生产环境通过 `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_DATA_REDIS_DATABASE`、`SPRING_DATA_REDIS_PASSWORD` 提供实际连接信息。多个后端实例共用登录时，需要使用相同Redis数据库、`JWT_SECRET`、签发者和缓存前缀。

登录缓存实现为 admin 的 `RedisLoginTokenStore`；framework 的 `RedisScriptExecutor` 统一执行字符串序列化的Lua脚本，失败返回503及 `AUTH_CACHE_UNAVAILABLE`，日志不输出连接密码或缓存内容。接口不会自动退回本机内存认证。

每个用户有两个键，例如 `personal-website:auth:{1}:tokens`（Hash）和 `personal-website:auth:{1}:expires`（Sorted Set）。Hash以随机登录标识为字段，值只存密码指纹和两个截止时间；不保存明文密码、BCrypt哈希、完整JWT或用户资料。索引按滑动截止时间排序，保存和续期时清理已过期字段，两个键的TTL以该用户最晚的登录截止时间设置；每个登录还单独校验有效期，不能借用其他登录的时效。

续期与注销使用Lua原子操作：续期必须先检查记录仍存在且未过期，退出只移除当前字段，改密删除该用户的两个键，不扫描或清空其他项目的Redis数据。数据库密码更新和Redis撤销不是分布式事务，鉴权时的数据库密码指纹校验继续保留，缓存恢复后旧密码Token仍无法访问。

后端重启后，Redis中尚未过期的登录可继续使用，前提是JWT密钥及配置不变、Redis记录仍在。首次从内存切换为Redis后需重新登录一次，无须改表或执行SQL。本次仅配置代码，未测试网络连接或执行Redis命令。

## 管理员认证接口

后端已配置控制台和滚动文件日志，默认位于 `logs/application.log`。支持 API 请求耗时、管理员操作结果、请求 ID 与内部错误调用位置；配置及排查方法见 [日志说明](docs/logging.md)。

后端使用 Spring MVC 提供 HTTP 接口，默认端口为 `8080`。按上方步骤启动 API 模块。接口文档见 `docs/openapi.yaml`，可导入 Apifox。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 管理员登录，JSON 请求体包含 `username`、`password` |
| GET | `/api/auth/me` | 获取当前管理员，需要 `Authorization: Bearer <token>` |
| PUT | `/api/auth/me` | 修改当前管理员的登录账号、昵称和邮箱 |
| PUT | `/api/auth/me/password` | 校验当前密码后修改密码，成功后重新登录 |
| POST | `/api/auth/logout` | 撤销当前 Token，无响应体，返回 204 |

登录示例（请使用自行创建的账号和密码）：

```json
{"username":"admin","password":"your-admin-password"}
```

登录成功返回如下结构；时间为 Unix 毫秒，用户 ID 为字符串，资料不包含密码哈希：

```json
{
  "code": "OK",
  "message": "登录成功",
  "data": {
    "token": "<JWT>",
    "tokenType": "Bearer",
    "expiresAt": 1791541800000,
    "maxExpiresAt": 1791568800000,
    "user": {"id": "1", "username": "admin", "role": "ADMIN", "status": "ACTIVE"}
  }
}
```

Apifox 后续请求使用 Bearer Token 认证，填入 `data.token`。未登录、签名错误、过期或已注销返回401，权限撤销返回403，登录限流返回429。登录失败统一提示“账号或密码错误”。

修改账号信息：携带 Bearer Token 向 `PUT /api/auth/me` 提交 `{"username":"admin","nickname":"曹进龙","email":"admin@example.com"}`。仅能修改当前账号，不接收目标用户 ID、角色或状态。昵称及邮箱可为空（省略也会清空）；账号不能为空且不超过100字符，邮箱格式必须有效且不超过254字符。账号重复返回409。保存后返回最新用户资料，不改变当前 Token 或密码。此功能无需变更表结构，也不会修改前台公开的个人介绍。

### 初始化管理员

后台“个人资料”下方提供“修改密码”。接口请求包含 `currentPassword`、`newPassword`、`confirmPassword` 三个字段；新密码至少8个Unicode字符且不超过72个UTF-8字节，必须两次一致，并与旧密码不同。成功返回 `OK`、`data: null`，撤销该账号所有缓存中的 Token，前端清除 Token 并返回登录页。每次鉴权还会校验数据库密码指纹，使并发签发的旧密码 Token 在下一次请求时失效；当前密码错误返回400，不会立即退出当前登录。修改接口每个用户每5分钟限制20次请求。

无需执行新的SQL。部署 JWT 更新后，需要重新登录一次。相关逻辑位于 admin 模块的 `AdminPasswordService`、`TokenService`、`SysProfileController` 和 `AdminAuthInterceptor`；操作日志记录 `ADMIN_PASSWORD_UPDATE`，不记录任何密码字段或令牌。并发更新使用旧密码哈希作为条件，避免覆盖其他请求已保存的新密码。

不提供公开注册接口，不自动创建默认管理员。密码使用 BCrypt 哈希，支持标准 `$2a$` / `$2b$` / `$2y$` 哈希以及可选的 `{bcrypt}` 前缀。admin 模块提供 `admin/tools/PasswordHashGenerator.java`，通过交互式终端输入密码并生成哈希，不需要数据库连接。先按上方步骤安装模块依赖，再执行：

```powershell
./mvnw.cmd -pl personal-website-admin dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt"
$runtimeClasspath = (Get-Content personal-website-admin/target/runtime-classpath.txt -Raw).Trim()
java -cp "personal-website-admin/target/classes;$runtimeClasspath" com.jinlong.personalwebsitesys.admin.tools.PasswordHashGenerator
```

生成哈希后，在 Navicat 中替换下面的占位符，再手动执行：

```sql
INSERT INTO public.sys_user (username, password_hash, nickname, role, status)
VALUES ('admin', '替换为生成的BCrypt哈希', '曹进龙', 'ADMIN', 'ACTIVE');
```

账号按不区分大小写查询，登录前会去除两端空白；密码保持原样且不超过72个UTF-8字节。

### JWT 与部署配置

参考本机若依工程的“JWT 标识 + 服务端登录缓存”方式，JWT 使用 HS512 签名，只携带随机登录标识、用户ID、签发者及时间，不携带密码、邮箱或权限快照。服务端每次检查签名、缓存时效、数据库中的 ACTIVE ADMIN 状态和密码指纹，鉴权入口为 `AdminAuthInterceptor`。`/api/**` 默认要求有效管理员 Token，只有登录、退出和预检请求放行。后续增加公开接口时，需要明确配置访问范围。

- `token.expire-time: 30`：服务端登录缓存有效期，单位分钟。
- `token.refresh-threshold: 20`：剩余有效期不超过20分钟时，受保护请求将其续期至当前时间后30分钟；不会恢复已过期或已注销的 Token。
- `token.max-lifetime: 480`：登录最长保留8小时，JWT的 `exp` 使用该截止时间，续期不会超过它。
- 登录状态使用 `RedisLoginTokenStore`，通过Redis共享、自动过期及原子续期和撤销；后端重启不主动删除缓存。Redis不可用时返回503，前端保留Token供服务恢复后重试。
- 受保护请求响应头返回 `X-Token-Expires-At`、`X-Token-Max-Expires-At`（Unix毫秒），前端据此更新显示；JWT本身在滑动续期时保持不变，没有独立的刷新接口。
- `token.secret`：至少64字节随机值的 Base64 编码，本机已写入 Git 忽略的 dev 配置，`JWT_SECRET` 可覆盖。生产环境必须设置独立随机密钥，缺失或无效时拒绝启动。
- `AUTH_ALLOWED_ORIGINS`：以逗号分隔的可信前端来源，必须包含协议和端口（如 `https://www.caojinlong.top`），无末尾斜杠。默认允许本机 5173 和 8080 端口。
- 浏览器写请求校验 Origin；Apifox 等非浏览器客户端可不带 Origin，若带有则必须匹配配置。
- 前端参考若依 Vue3，将 API、Token 工具、Axios 请求拦截和路由鉴权分层；Token及其时效保存到 `sessionStorage`，密码和请求体不缓存。刷新页面后会携带 Token 到 `/auth/me` 校验资料。浏览器存储禁用时仅保留当前页面内存状态。
- 生产部署使用 HTTPS 和同源反向代理；跨域需允许 `Authorization` 请求头，前端不再启用 `withCredentials`。
- 当前限流为单实例按直接来源 IP 每5分钟20次；代理后可能共享来源 IP，多实例和线上代理需配合网关限流。
- `token.key-prefix` 默认 `personal-website:auth`，可通过 `AUTH_REDIS_KEY_PREFIX` 按项目或环境隔离缓存。

配套前端已接入这些接口，登录后跳转到后台工作台。本地前端通过 Vite 的 `/api` 代理调用 8080 端口；生产环境需配置反向代理和可信来源。未启动后端、执行SQL或运行测试。
