# AI 业务模块

当前仅预留 Maven 模块，尚未实现接口或接入模型服务，也未加入 API 启动模块的运行依赖。

后续代码使用 `com.jinlong.personalwebsitesys.ai` 包，按 `controller`、`domain/dto`、`domain/vo`、`service/impl`、`mapper` 等职责分层。接入运行时，在 `personal-website-api/pom.xml` 添加本模块依赖；按需引用 framework 与持久化依赖，独立配置 Mapper 扫描。接口使用 `/api/ai/**`，权限与限流需明确配置。
