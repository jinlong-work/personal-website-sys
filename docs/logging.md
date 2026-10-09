# 后端日志

## 配置与位置

继续使用 `application.yml`、`application-dev.yml` 两个应用配置文件，位置为 `personal-website-api/src/main/resources`；日志配置集中在公共 `application.yml`，不新增 Logback XML 或依赖。

默认输出到控制台和启动工作目录下的 `logs/application.log`。可用 `LOG_PATH` 指定日志目录。时间以 Asia/Shanghai 显示，文件编码为 UTF-8。

文件按日期和大小滚动，单文件达到10MB后归档到 `logs/archive/` 并压缩；保留最近14天的历史归档，归档总量上限1GB。日志文件已加入 Git 忽略。

## 日志类型

| Logger | 用途 | 内容 |
| --- | --- | --- |
| `audit.access` | API 请求日志 | HTTP 方法、路由模板、状态码、耗时、直接来源 IP、业务错误码 |
| `audit.operation` | 关键操作结果 | 管理员登录、退出、资料修改、密码修改的成功或失败及请求概况 |
| 业务类 Logger | 应用诊断 | 启动日志、内部错误类型和调用位置 |

请求日志按响应状态区分 INFO（成功/重定向）、WARN（4xx）、ERROR（5xx）。操作日志成功为 INFO，失败为 WARN。过滤器通过最终响应状态记录结果，登录失败、限流和未授权请求同样会留下记录。

每个 API 请求在服务端生成 UUID，通过 MDC 的 `requestId` 加入日志，并通过响应头 `X-Request-ID` 返回；CORS 已允许浏览器读取此响应头。admin 的鉴权拦截器关联当前用户ID和操作名称，framework 统一输出，不引用具体业务模块。未登录显示 `anonymous`。在进入业务拦截器前被CORS拒绝的请求只有访问日志，不会标记为业务操作。

示例：

```text
2026-10-09 16:30:00.123 INFO [http-nio-8080-exec-1] [requestId=示例UUID] [userId=1] audit.operation - operation=ADMIN_PROFILE_UPDATE outcome=SUCCESS method=PUT route=/api/auth/me status=200 durationMs=25 ip=127.0.0.1 code=OK
```

## 敏感信息与排查

应用自定义日志不读取或打印请求体、查询参数、响应体、Cookie、Authorization、JWT密钥、登录密码、密码哈希、邮箱或数据库凭据。记录路由模板而非原始 URL，避免路径变量或查询字符串带入敏感数据。来自请求的日志字段会截断并过滤控制字符。

内部错误通过 `SafeLogUtils` 输出异常类名、有限长度的调用栈和原因链；SQL 异常额外输出 SQLState 与厂商错误码。不会直接传入原始 Throwable 或异常消息，以免数据库异常中的 SQL 参数进入日志。业务异常仅记录稳定的业务错误码，不输出请求值。

排查方式：先查看失败接口响应头中的 `X-Request-ID`，再在日志中查找相同 ID；同一请求的访问、操作和异常记录使用同一标识。MDC 在请求完成后恢复，避免 Servlet 线程池复用导致标识串用。

来源 IP 使用 `getRemoteAddr()`，不会信任客户端提供的 `X-Forwarded-For`。部署在反向代理后可能显示代理 IP，配置可信代理转发需要单独结合实际部署处理。

当前覆盖同步 Servlet API 请求。将来添加异步接口或后台异步任务时，需要另外传播日志上下文并记录异步完成结果。操作日志目前写文件，不写数据库，也没有后台日志查询页面。

## 代码位置

- common 模块的 `common/constant/LogConstants`：MDC 和请求头常量。
- common 模块的 `common/utils/SafeLogUtils`：日志字段处理和安全异常诊断。
- framework 模块的 `framework/web/filter/RequestLogFilter`：请求追踪、耗时、访问与操作结果。
- framework 模块的 `framework/web/exception/GlobalExceptionHandler`：设置错误码与记录内部异常。
- admin 模块的 `admin/security/interceptor/AdminAuthInterceptor`：提供管理员操作名称及用户ID。
- framework 模块的 `framework/redis/RedisScriptExecutor`：Redis故障的安全诊断及503业务错误，不记录键、参数或原始连接异常消息。

按要求仅完成代码与配置，未启动应用、生成实际日志或运行测试。
