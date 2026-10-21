# 个人网站后台

按 RuoYi-Vue 的模块与登录协议组织，使用 JDK 25、Spring Boot 4.1.1、Spring Security、MyBatis-Plus、PostgreSQL 和 Redis。

## 当前模块

- `personal-website-admin`：启动入口、登录控制器、验证码控制器与应用配置。
- `personal-website-framework`：Spring Security、JWT、验证码配置、密码重试限制与异常处理。
- `personal-website-system`：用户、菜单、配置服务及 Mapper XML。
- `personal-website-common`：若依响应、实体、登录模型、RedisCache 与工具。

唯一启动类是 `com.jinlong.web.PersonalWebsiteSysApplication`，扫描 `com.jinlong` 下的各模块。登录相关类名、接口路径与响应字段沿用若依。详细说明见 [架构说明](docs/architecture.md)。

## 先执行 PostgreSQL SQL

1. 在业务数据库执行 [003_ruoyi_login_postgresql.sql](sql/003_ruoyi_login_postgresql.sql)。新库创建登录所需8张表；若已执行旧001脚本，迁移原账号字段并保留ID、BCrypt哈希、邮箱、状态和角色。建议先备份原用户表。若旧ID=1不是ADMIN，脚本会整体回滚，请人工核实权限与ID。
2. 若已有管理员，继续使用原账号与原密码；若没有管理员，在 [004_ruoyi_initial_admin_postgresql.sql](sql/004_ruoyi_initial_admin_postgresql.sql) 中替换 `initial_password` 后执行。需要 `pgcrypto` 扩展；同名用户或ID=1已存在时中止，不覆盖账号。
3. 执行 [005_user_management_postgresql.sql](sql/005_user_management_postgresql.sql)，初始化三种角色与8个业务权限，统一角色关联并增加用户管理约束。脚本会先在同库私有schema备份相关数据，再在事务内迁移；成功执行后可重复调用并跳过变更。
4. 当前结构不再执行历史001、002脚本。应用不自动运行SQL。

表为 `sys_user`、`sys_dept`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`、`sys_config`、`sys_logininfor`，默认使用 `public` schema。用户状态 `0` 正常、`1` 停用；删除标记 `0` 存在、`2` 删除。若依以用户ID=1作为超级管理员。

脚本不导入若依演示账号和演示菜单。初始 `sys_menu` 为空，因此 `/getRouters` 返回空数组。后台用户管理采用固定三种角色及服务端范围校验，不依赖演示菜单；仍不开放公开注册。

## 首页项目管理

新增项目内容、中英文说明、效果图图集、封面、精选展示、排序及发布/下架管理，首页从`GET /public/projects`读取已发布数据。已实际创建项目和图片资源表，将现有8个项目及27张效果图迁入，并配置私有MinIO项目桶`website-projects`。功能、接口、迁移记录及新环境初始化见[项目管理说明](docs/project-management.md)。

项目代码已完成前后端编译检查，未运行测试。启动后台使用JDK25；重启前后端后可进入后台“项目管理”。

## 用户管理接口

新增用户台账、账号维护、密码重置、角色设置与启停接口，见 [用户管理接口与权限](docs/user-management.md)。本人账号资料及密码修改也已接入。

用户台账已使用 MyBatis-Plus `Page` / `IPage` 和 PostgreSQL 分页插件，统一处理分页及总数查询，每页最多100条。后续业务优先采用 MyBatis-Plus 的通用 CRUD、Lambda 条件构造与分页能力；复杂 SQL 保留 XML 实现，约定见 [架构说明](docs/architecture.md) 和 [开发约定](AGENTS.md)。

角色沿用 `admin`（超级管理员）、`common`（普通用户），新增 `manager`（管理员）。005迁移已初始化角色及权限数据；业务写入仍按角色标识查找记录，不假定角色编号。操作权限读取数据库的角色—权限关系，接口注解与业务范围校验共同控制访问。

已于2026-10-15对当前配置的业务库实际执行005迁移并只读核对，原账号和密码哈希保持不变，见 [迁移执行记录](docs/database-migration-005.md)。未运行应用测试、构建或启动服务；后端代码需要重启后使用。

## 配置与启动

配置在 `personal-website-admin/src/main/resources`：公共 `application.yml` 与 Git 忽略的本机 `application-dev.yml`。保留现有本机数据库、Redis和密钥配置，默认启用dev。

生产环境设置 `SPRING_PROFILES_ACTIVE=prod`，提供 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_DATA_REDIS_PASSWORD`、`JWT_SECRET` 与 `AUTH_ALLOWED_ORIGINS`。JWT密钥至少64个UTF-8字节。Redis需要允许GET/SET/DEL/EXPIRE及EVAL。验证码消费与密码错误计数使用Lua，兼容Redis 5。

项目图片复用既有MinIO连接，`MINIO_PROJECT_BUCKET`默认为`website-projects`。新环境完成005后执行006项目迁移并初始化内容，详见项目管理说明；Nginx上传限制调整为12m，头像本身仍限制2MiB。

```powershell
# 根目录，使用JDK 25；此工程使用本机Maven，不再依赖已删除的mvnw。
mvn test
mvn -pl personal-website-admin -am install -DskipTests
mvn -pl personal-website-admin spring-boot:run

# 发布构建必须clean，防止旧target/classes中残留dev凭据。
mvn -Prelease clean package
java -jar personal-website-admin/target/personal-website-admin-0.0.1-SNAPSHOT.jar
```

`release` profile排除 `application-dev.yml`。`release/personal-website-api.jar` 已更新为当前admin模块的构建产物，文件名沿用以兼容已有Docker部署。生产环境的MinIO配置和Nginx代理规则见 [发布说明](release/README.md)。

## 登录协议

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/captchaImage` | 匿名获取验证码，返回 `captchaEnabled`、`uuid`、`img` |
| POST | `/login` | JSON字段 `username`、`password`、`code`、`uuid`，成功返回顶层 `token` |
| GET | `/getInfo` | 返回 `user`、`roles`、`permissions`及密码提醒字段 |
| GET | `/getRouters` | 返回若依 `RouterVo` 格式的菜单树 |
| POST | `/logout` | 撤销当前会话，返回 `code:200` |

成功响应使用若依 `AjaxResult` 的数字 `code` 与 `msg`；后续请求使用 `Authorization: Bearer <token>`。登录示例：

```json
{"username":"admin","password":"你的登录密码","code":"验证码答案","uuid":"captchaImage返回的uuid"}
```

验证码图片使用 `data:image/jpeg;base64,<img>` 显示。默认数学验证码，有效期2分钟，提交后无论对错均消费一次；切换字符验证码设置 `ruoyi.captchaType: char`。验证码开关取 `sys_config` 的 `sys.account.captchaEnabled`；直接改配置表后需重启应用更新缓存。

用户名不区分大小写，不自动去掉账号两端空白；密码至少5个字符且UTF-8编码不超过72字节，使用BCrypt。5次错误后限制10分钟，同一账号不同大小写共用计数。错误账号、错误密码及停用账号使用统一失败提示。

JWT仅携带随机登录标识与账号，Redis存储服务端会话。默认30分钟过期，剩余20分钟内续期，最长8小时重新登录。退出删除当前会话；续期不会重建已退出会话。鉴权重新查询用户与权限，因此停用、删除、密码哈希或角色权限变化对下一次请求生效。所有认证缓存使用 `token.key-prefix` 隔离；`user.password.maxRetryCount` 与 `lockTime` 可配置。

未登录或无效Token返回HTTP 401；权限不足403；验证码/参数错误400；重试限制429；数据库或Redis不可用503。`/getInfo` 不返回密码哈希。完整接口文档在 [OpenAPI](docs/openapi.yaml)，可导入Apifox。

Apifox 可导入 [OpenAPI JSON](docs/apifox-login.openapi.json) 或上述 YAML，两份内容一致。具体操作顺序与错误排查见 [Apifox 测试步骤](docs/apifox-testing.md)。

Apifox“个人网站接口”项目的“登录认证”目录已按当前源码更新。最新离线文档为 [认证 OpenAPI 1.3](docs/apifox-auth.openapi.json)，使用步骤见 [认证接口联调指南](docs/apifox-auth-guide.md)，包含验证码图片展示、自动保存 Token 和退出后的失效验证。

现有前端若仍调用 `/api/auth/*`，需要改为上述若依路径，并读取顶层 `token`、`msg` 和数字 `code`。本次修改集中在后台工程。

## 验证

登录流程测试隔离数据库和Redis，覆盖真实MVC、安全过滤器、BCrypt、JWT、密码字段隐藏、验证码、注销、重试限制、账号状态、权限路由和CORS。

可选Redis集成测试使用单独的本机测试实例，通过 `mvn -Dtest.redis.port=端口 test` 验证缓存序列化、Lua单次消费和失败计数。不要使用业务Redis端口。SQL已通过PostgreSQL SQL/PLpgSQL解析器语法检查；未在你的远程PostgreSQL数据库执行，实际建表和迁移请按上述步骤完成。
