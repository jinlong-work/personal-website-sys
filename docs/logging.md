# 后端日志

日志配置位于 `personal-website-admin/src/main/resources/application.yml`，输出控制台及工作目录 `logs/application.log`；可用LOG_PATH覆盖。时间为Asia/Shanghai，文件编码UTF-8。按10MB和日期归档，保留14天，最多1GB。

登录成功、验证码失败、账号密码失败、重试限制与退出写入 `sys_logininfor`，记录账号、直接来源IP、状态、提示及时间。不记录明文密码、完整JWT、验证码答案或数据库凭据。目前浏览器、系统与地理位置字段为空，未调用外部IP定位服务。

失败提示沿用AjaxResult的code/msg。日志记录数据库/Redis访问错误和内部异常堆栈；登录审计写入失败记录WARN。认证缓存不可用时返回503，不退回内存会话。

当前未接入旧架构的RequestLogFilter与MDC请求ID链路，因此日志格式中的requestId/userId占位符显示默认值；不再承诺旧版X-Request-ID与Token有效期响应头。后续可在framework统一加入请求追踪。