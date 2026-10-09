# 后端模块结构

采用 Maven 父工程加业务模块的方式组织。根工程 `personal-website-sys` 仅聚合模块、管理版本和构建规则；`personal-website-api` 为唯一 Spring Boot 启动入口。

```text
personal-website-sys/
├── pom.xml                              # 父工程，packaging=pom
├── personal-website-api/                 # 启动入口
│   └── src/
│       ├── main/java/.../PersonalWebsiteSysApplication.java
│       ├── main/resources/application.yml
│       ├── main/resources/application-dev.yml  # 本机凭据，Git忽略
│       └── test/                        # 原有启动测试
├── personal-website-admin/               # 当前管理员业务
│   └── src/main/java/.../admin/
│       ├── controller/                  # 登录、退出、资料查询和修改
│       ├── domain/                      # SysUser实体
│       │   ├── dto/                     # 登录与资料更新请求
│       │   └── vo/                      # 对外资料，排除密码哈希
│       ├── mapper/                      # SysUserMapper（MyBatis-Plus）
│       ├── service/                     # ISysUserService
│       │   └── impl/                    # 业务校验、事务与持久化
│       ├── security/
│       │   ├── domain/                  # 登录缓存对象
│       │   ├── store/                   # LoginTokenStore接口与Redis实现
│       │   ├── service/                 # JWT签发、续期、撤销、登录认证与限流
│       │   └── interceptor/             # Bearer JWT、管理员权限与密码指纹检查
│       ├── config/                      # 当前业务Mapper扫描、鉴权注册
│       └── tools/                       # BCrypt哈希生成器
├── personal-website-common/              # 公共响应、常量、异常、工具
│   └── src/main/java/.../common/
├── personal-website-framework/           # 可复用技术基础设施
│   └── src/main/java/.../framework/
│       ├── config/                      # CORS与密码编码器
│       ├── security/                    # 可信来源规则
│       ├── redis/                       # Redis脚本执行与安全异常处理
│       └── web/
│           ├── exception/               # 全局异常处理
│           └── filter/                  # 请求追踪、耗时与统一日志
├── personal-website-modules/             # 后续业务聚合，packaging=pom
│   ├── personal-website-ai/              # 预留，尚无功能
│   └── personal-website-map/             # 预留，尚无功能
├── sql/                                 # 手动执行的数据库脚本
└── docs/                                # OpenAPI、架构、日志说明
```

## 依赖关系

```text
api → admin → framework → common
modules（聚合）
  ├── ai → common
  └── map → common
```

业务模块通过 Maven 依赖进入启动模块，只有 api 配置 Spring Boot 打包插件；其他 Java 模块输出普通 JAR。父工程不直接声明业务依赖，避免所有模块继承无关依赖。

common 不引用 Spring 或业务模块；framework 不引用 admin、ai、map，负责通用日志、异常、Redis和 Web 基础设施。管理员认证、登录缓存格式与Lua业务脚本、用户Mapper、账号资料与操作名称归入 admin。

AI 和地图目前只预留 POM 和开发说明，没有假接口或模型调用，api 暂不依赖它们。后续实现业务后，在 api 的 POM 增加相应模块依赖即可接入统一启动入口。若需持久化，各模块自行声明 MyBatis-Plus 依赖和 Mapper 扫描；模块间共享能力通过明确的服务接口暴露，避免相互引用实体或 Mapper。

## 模块内规范

业务按 `controller → service接口 → service/impl → mapper` 调用，数据库对象在 `domain`，请求在 `domain/dto`，响应在 `domain/vo`。

- Controller 负责 HTTP 输入输出，调用服务签发或撤销 Token，不直接操作数据库。
- Service 负责业务校验和事务，Mapper 负责数据库访问。
- 数据库实体不直接作为接口响应；密码哈希只用于内部认证。
- DTO 不接受客户端指定管理员ID、角色和账号状态。
- 框架统一记录日志，业务通过请求属性 `AUDIT_OPERATION` 标记操作名称，框架不硬编码管理员业务接口。

现有链路：登录经过 `SysLoginController → AdminAuthService → ISysUserService → SysUserMapper`；资料修改经过 `SysProfileController → ISysUserService → SysUserMapper`。

## MyBatis-Plus 与配置

沿用 Spring Boot 4 对应的 `mybatis-plus-spring-boot4-starter`，版本由父 POM 统一管理。`admin.config.MybatisPlusConfig` 扫描 `com.jinlong.personalwebsitesys.admin.mapper`。用户表仍为 `public.sys_user`，主键使用 PostgreSQL Identity，无需改表或迁移数据。

Mapper 继承 `BaseMapper<SysUser>`，当前查询与更新使用条件构造器，不创建无用途的 XML。账号查询通过 `.apply("LOWER(username) = LOWER({0})", username)` 绑定参数；更新限定当前用户ID、ADMIN角色和ACTIVE状态。昵称与邮箱可通过显式 `set(..., null)` 清空，重复账号依靠数据库唯一索引转换为409。

两个应用配置文件只位于 api 的资源目录；dev 已迁移并继续 Git 忽略，密码未写入公共配置。业务模块不额外定义 application 配置。生产环境仍通过 prod profile 和环境变量提供连接信息。

## 启动与兼容

在 IDE 中重新加载根 POM，运行 api 模块的原启动类，工作目录设置为后端根目录。启动类保留根包，能够扫描各依赖模块的组件。

命令行在根目录先执行 `./mvnw.cmd -DskipTests clean install` 安装内部模块，再执行 `./mvnw.cmd -pl personal-website-api spring-boot:run`。不要直接在父工程执行 spring-boot:run，也不要带 `-am` 对所有模块执行该启动目标。

接口路径、SQL脚本和数据库密码格式保持不变。登录响应改为 `data.token/tokenType/expiresAt/maxExpiresAt/user`；资料接口仍返回 `data` 中的用户资料。认证统一为 `Authorization: Bearer <token>`，前后端需同步更新。当前 `/api/**` 要求管理员 Token，只有登录、退出与预检请求放行；后续AI或地图接口开放前必须明确各自的权限范围。

参考若依登录缓存机制，`LoginTokenStore` 用Redis实现，采用用户登录Hash及过期索引，Lua原子执行查找、续期和撤销，不会让并发请求恢复已注销的登录。多个后端实例共享Redis、JWT密钥、签发者和缓存前缀时可共享登录状态；重启不主动删除Redis记录。JWT绝对过期时间限制滑动续期。Redis连接、Token时效和本机密码继续使用现有两个应用配置文件，不增加第三个配置文件。

`framework.redis.RedisScriptExecutor` 不引用管理员实体，负责字符串序列化执行与安全诊断；admin决定键、字段格式、TTL和具体Lua脚本。Redis不可用返回503，不降级为内存认证。登录限流仍为单实例实现，尚未迁移到Redis。

按用户要求未运行编译、构建、测试或远程数据库操作，仅进行文件迁移和静态引用核对。
