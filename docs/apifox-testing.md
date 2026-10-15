# Apifox 登录接口测试

Apifox“个人网站接口”项目的“登录认证”目录已按现有源码同步，不必重复导入。请优先查看 [认证接口联调指南](apifox-auth-guide.md)，了解已配置的验证码图片展示与 Token 提取脚本。最新离线备份为 [认证 OpenAPI 1.3](apifox-auth.openapi.json)，包含完整的部门、角色和递归路由模型。

需要在其他项目导入时，也可使用历史 OpenAPI 3.0 文件 `apifox-login.openapi.json` 或 `openapi.yaml`，两份历史文件内容一致。文档包含当前已实现的5个接口、请求示例、响应字段、错误示例和Bearer认证定义。

如果导入后没有看到登录接口，先导入 [登录单接口文件](apifox-login-only.openapi.json)。这份文件只有 `POST /login`，不引用外部数据模型。也可以导入 [新版完整文件](apifox-auth-v2.openapi.json)，第一项即为登录，共5个接口，归入“网站认证（新版）”目录。

在已有项目中，进入“项目设置 → 导入数据”，使用 OpenAPI/Swagger 文件导入。可视化导入的预览中检查是否识别到 `POST /login`；确认目标分支和模块。同方法、同路径冲突时，“不导入”会跳过新接口，需按实际情况选择“覆盖已有接口”或“保留两者”。具体选项见 [Apifox官方导入说明](https://docs.apifox.com/import-openapi-swagger)。原始文件经核对包含 `/login`，实际导入遗漏原因仍需结合导入预览和冲突选项判断。

服务地址默认 `http://localhost:8080`；如果修改了端口或使用服务器部署，将服务地址换成实际地址。先启动后台，手动执行 `sql/003_ruoyi_login_postgresql.sql`；没有管理员时，替换密码后执行 `sql/004_ruoyi_initial_admin_postgresql.sql`。已有旧账号迁移后继续使用原密码。

## 1. 获取验证码

发送 `GET /captchaImage`，不需要认证、请求体或查询参数。

- `captchaEnabled=true`：保存响应中的 `uuid`；将 `img` 拼接为 `data:image/jpeg;base64,<img>` 后，使用支持Base64图片显示的工具查看。数学验证码填写计算结果，字符验证码填写图中文字。
- `captchaEnabled=false`：登录时可以省略 `code`、`uuid`。

验证码不会明文返回答案，有效期2分钟。每次登录尝试都会消费一次验证码；即使密码错误，也需要重新获取。

## 2. 登录

发送 `POST /login`，不需要认证。请求体选择JSON，`Content-Type: application/json`：

```json
{
  "username": "admin",
  "password": "替换为你的实际后台登录密码",
  "code": "替换为验证码答案",
  "uuid": "替换为刚才返回的uuid"
}
```

文档导入后的示例值是占位值，必须替换。验证码关闭时仅发送 `username` 和 `password`。

成功响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "token": "<实际JWT>"
}
```

复制顶层 `token`。数据库连接密码和后台账号密码不同，后台使用管理员账号对应的密码。

## 3. 查询当前用户

发送 `GET /getInfo`。在Bearer认证中填写原始Token；若手动设置请求头，使用：

```text
Authorization: Bearer <实际JWT>
```

不需要请求体或查询参数。成功返回顶层 `user`、`roles`、`permissions`。用户ID=1对应超级管理员，角色含 `admin`，权限含 `*:*:*`；响应不包含密码哈希。

## 4. 查询菜单路由

使用同一个Token发送 `GET /getRouters`，没有请求参数。

初始SQL没有预置演示菜单，返回 `{"code":200,"msg":"操作成功","data":[]}` 属于正常结果。配置菜单及角色授权后才会返回对应路由。

## 5. 退出并验证失效

使用同一个Token发送 `POST /logout`，没有请求体。成功返回 `{"code":200,"msg":"退出成功"}`。

再用原Token调用 `/getInfo`，应返回HTTP 401，证明当前会话已撤销。没有携带Token调用退出接口也会返回成功，因此验证退出时必须携带实际登录Token。

## 常见失败

| HTTP/code | 场景 | 处理 |
| --- | --- | --- |
| 400 | 验证码错误、失效、已消费或JSON错误 | 重新获取验证码，检查请求体 |
| 401 | 用户名密码错误、账号停用，或受保护接口Token无效 | 核对账号密码，重新登录并设置Bearer认证 |
| 403 | IP禁止登录或没有访问权限 | 检查登录IP黑名单或权限配置 |
| 429 | 默认5次密码错误后限制10分钟 | 等待限制时间结束，再用正确密码登录 |
| 503 | PostgreSQL或Redis不可用 | 检查后台连接配置、服务状态和日志 |
| 500 | 其他内部异常 | 查看后台日志定位原因 |

需要临时关闭验证码时，将 `sys_config` 中 `sys.account.captchaEnabled` 的 `config_value` 改为 `false`，然后重启后台刷新参数缓存。测试完成后可恢复为 `true`。
