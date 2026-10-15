# 认证接口联调指南

文档按当前 `personal-website-sys` 后端源码核对，保留若依的路径与字段命名。项目：个人网站接口（8904772），主分支，登录认证目录。

## 接口清单

| 顺序 | 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/captchaImage` | 无 | 获取验证码图片和 uuid |
| 2 | POST | `/login` | 无 | 登录，返回顶层 token |
| 3 | GET | `/getInfo` | Bearer | 获取用户、角色、权限和密码提醒 |
| 4 | GET | `/getRouters` | Bearer | 获取当前用户的菜单路由树 |
| 5 | POST | `/logout` | Bearer，撤销会话时需携带 | 退出当前会话 |

历史 `/api/auth/me` 和 `/api/auth/logout` 未在当前后端实现，已标记为废弃；分别使用 `/getInfo` 和 `/logout`。

## 选择环境

本地后端默认 `http://localhost:8080`，Apifox 的“测试环境”用于该地址。使用服务器时选择相应环境，并核对实际端口或反向代理配置。登录联调请选择真实后端环境。

发送请求前启动后端，并确保 PostgreSQL、Redis 和后台管理员账号可用。数据库连接密码与后台账号密码是两种不同凭证。

## 验证码与图片展示

1. 发送 `GET /captchaImage`，无需请求参数。
2. `captchaEnabled=true` 时，响应包含 `uuid` 和 `img`。后置脚本自动保存环境变量 `pw_captcha_uuid`，在响应的 **Visualize / 可视化** 页展示图片。
3. 图片为数学题时填写计算结果，字符图则填写图中文字；不会返回验证码答案。
4. 验证码有效期 2 分钟，在登录时验证并一次性消费。验证码错误或账号密码错误后都要重新获取。
5. `captchaEnabled=false` 时不返回图片与 uuid，登录请求可删除 `code` 和 `uuid`。

`img` 是 JPEG 图片的 Base64 编码，不含 URL 前缀。网页可这样展示：

```js
imageElement.src = 'data:image/jpeg;base64,' + response.img;
```

文档中的验证码响应图片是“1+5=?”的模拟示例，仅用于说明展示方式；真实登录必须使用真实接口新生成的图片和 uuid。可视化用法参见 [Apifox 官方文档](https://docs.apifox.com/response-data-visualization)。

## 登录与 Token

发送 `POST /login`，请求体为 JSON：

```json
{
  "username": "admin",
  "password": "REPLACE_WITH_YOUR_PASSWORD",
  "code": "6",
  "uuid": "{{pw_captcha_uuid}}"
}
```

将用户名、密码及 `code` 改为实际值；`6` 只是示例答案。账号为 2～100 个字符，大小写不敏感、不允许两端空白；密码至少 5 个 Java 字符，UTF-8 编码不超过 72 字节，不自动去除空格。验证码启用时 `code` 与 `uuid` 必填。

成功返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "token": "实际JWT"
}
```

后置脚本只在登录成功时将顶层 `token` 保存到当前环境变量 `pw_token`。后三个接口已配置 **Bearer Token：`{{pw_token}}`**，可直接发送。无需把 `Bearer` 前缀再填入 Token 输入框。可手动设置请求头 `Authorization: Bearer <实际JWT>`。脚本提取方式参见 [Apifox 后置脚本](https://docs.apifox.com/5581000m0)。

会话默认 30 分钟有效；受保护请求在剩余有效期不超过 20 分钟时续期，最长 8 小时。Token 还依赖 Redis 会话，签名有效不代表会话一直有效。账号停用、删除、改密及权限变更会在后续请求中生效。

## 用户与路由

`GET /getInfo` 没有请求参数，返回顶层 `user`、`roles`、`permissions`、`pwdChrtype`、`isDefaultModifyPwd`、`isPasswordExpired`。`user.roles` 是角色对象数组，顶层 `roles` 是角色标识字符串数组；权限集合顺序不固定。用户 ID=1 的超级管理员角色为 `admin`、权限包含 `*:*:*`，响应不包含密码哈希。

`GET /getRouters` 没有请求参数，返回 `data` 路由数组。初始化 SQL 不预置演示菜单，所以 `data=[]` 属于正常结果。菜单的 `children` 是递归同结构数组；空属性可能省略，`query` 是序列化后的 JSON 字符串。

## 退出并验证

携带同一个 Token 发送 `POST /logout`，无请求体。成功返回 `{"code":200,"msg":"退出成功"}`。

为方便验证，脚本保留 `pw_token` 的原值。再次调用 `/getInfo` 应返回 HTTP 401、`{"code":401,"msg":"请先登录"}`。重新登录会覆盖 `pw_token`。退出只撤销当前会话；不携带 Token 或 Token 已失效时也返回 200，但不会撤销其他会话。

## 错误排查

| HTTP / code | 常见说明 | 处理 |
| --- | --- | --- |
| 400 | 验证码错误、失效、已消费；JSON 或参数错误 | 重新获取验证码，检查请求体 |
| 401 | 账号密码错误、账号停用；Token 无效、已退出或过期 | 核对后台账号，重新登录 |
| 403 | 当前 IP 禁止登录、没有访问权限 | 检查 IP 黑名单及权限配置 |
| 429 | 默认连续 5 次密码错误后限制 10 分钟 | 等待限制结束后再使用正确密码 |
| 503 | 数据库或缓存服务暂不可用 / 认证服务暂不可用 | 检查 PostgreSQL、Redis、连接配置与日志 |
| 500 | 系统异常，请稍后重试 | 查看后端日志 |
| 404 / 405 | 已登录后访问不存在路径 / 使用错误 HTTP 方法 | 核对接口路径与方法 |

未认证访问其他路径可能先由安全过滤器返回 401。以上为源码确认的协议与文档配置，真实登录结果需在所选环境中执行验证。
