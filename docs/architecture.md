# 后端模块结构

依赖方向：`admin → framework → system → common`。父工程统一版本，admin为唯一可执行Spring Boot模块。

```text
personal-website-sys/
├── personal-website-admin/       com.jinlong.web：启动入口、HTTP接口、配置
├── personal-website-framework/   com.jinlong.framework：安全、Token、验证码、异常
├── personal-website-system/      com.jinlong.system：服务接口、实现、Mapper XML
├── personal-website-common/      com.jinlong.common：共享实体、AjaxResult、RedisCache
├── sql/                         PostgreSQL建表与迁移
└── docs/                        接口与运行说明
```

启动类扫描 `com.jinlong`，Mapper扫描 `com.jinlong.system.mapper`。MyBatis别名包括common中的entity与system中的domain；XML位置为 `classpath*:mapper/system/*Mapper.xml`。SQL使用PostgreSQL的 `CURRENT_TIMESTAMP`、`coalesce`、日期CAST和 `string_to_array`，菜单query字段用双引号。

后续业务优先使用 MyBatis-Plus：单表增删改查使用 `BaseMapper`，条件构造优先使用 Lambda Wrapper，分页统一使用 `Page` / `IPage`。新增实体应按实际表结构设置 `@TableName`、`@TableId` 和非持久化字段；共享的若依实体在接入通用 CRUD 前需确认字段映射，避免把角色集合等字段写入用户表。复杂关联、行锁和 PostgreSQL 特有语句可继续使用自定义 XML，分页查询仍接入插件。

`framework/config/MybatisPlusConfig` 注册全局 `MybatisPlusInterceptor`，使用 PostgreSQL 分页方言，每页最多100条，超出末页不自动跳回第一页。后续叠加其他内部拦截器时，分页插件放在最后。父工程统一管理 `mybatis-plus-jsqlparser` 版本，framework显式引入分页所需解析依赖。配置依据 [MyBatis-Plus 分页插件文档](https://baomidou.com/plugins/pagination/)。

登录流程：`CaptchaController → SysLoginController → SysLoginService → AuthenticationManager → UserDetailsServiceImpl → ISysUserService/SysUserMapper`。密码由Spring Security的BCryptPasswordEncoder验证。SysPasswordService使用Redis Lua累计失败次数；LoginLogService记录sys_logininfor。

TokenService签发HS512 JWT并缓存LoginUser。JwtAuthenticationTokenFilter校验签名、签发者、绝对有效期和Redis会话，再查询数据库确认用户状态、密码哈希和权限。SecurityConfig允许匿名访问POST /login、GET /captchaImage，其余请求鉴权；POST /logout通过安全链撤销会话。

复用若依实体、AjaxResult、LoginBody/LoginUser、SecurityUtils、验证码实现及菜单路由组装。用户与权限服务只实现当前登录阶段所需能力，不移入整个若依管理平台。已复制的其他Mapper保留为后续扩展基础，但003只创建本阶段需要的8张表，未接入的岗位、字典、公告等功能仍需后续建表与控制器。

配置文件位于admin模块；dev配置不提交Git。发布用 `mvn -Prelease clean package` 排除本机配置。前后端协议以docs/openapi.yaml为准；历史api模块、/api/auth接口及release中的旧JAR不属于本次架构。
