# 用户管理台账

沿用 `sys_user`、`sys_role`、`sys_user_role`、`sys_menu` 和 `sys_role_menu`，不新增业务表。先执行 `sql/005_user_management_postgresql.sql` 统一数据库约束与业务权限。响应沿用数值 `code`、`msg` 和 `data`。接口均需 Bearer Token。

## 角色范围

| 角色标识 | 名称 | 用户管理范围 |
| --- | --- | --- |
| admin | 超级管理员 | 全部用户，可设置其他用户的三种角色 |
| manager | 管理员 | 本人及普通用户，新增时只能分配普通用户 |
| common | 普通用户 | 本人账号资料与密码，无台账访问权限 |

用户 ID 为 1 的内置超级管理员不可停用或调整角色，其他超级管理员通过 `admin` 角色识别。所有用户均不能停用自己或修改自己的角色。管理员不能维护其他管理员、超级管理员，以及拥有其他旧角色的账号。

每次写入在事务内按用户编号锁定操作人和目标行，重新读取账号状态与角色后校验权限；账号与角色关系保持事务一致。台账结果白名单输出公开账号字段和 `canEdit`、`canResetPassword`、`canSetRole`、`canSetStatus`，不返回密码或哈希。前端按钮按这些字段显示可用状态，服务端不信任前端的权限判断。

## 台账接口

| 方法与路径 | 请求 | 行为 |
| --- | --- | --- |
| GET /system/users | keyword、role、status、pageNum、pageSize | 搜索及分页，返回 rows、total、roles 和 canCreate |
| POST /system/users | username、nickname、email、phone、status、role、password、confirmPassword、avatar | 创建用户及单个角色关系 |
| PUT /system/users/{userId} | username、nickname、email、phone、status、avatar | 编辑账号、联系信息和头像 |
| PUT /system/users/{userId}/role | role | 替换角色，限超级管理员操作其他非内置账号 |
| PUT /system/users/{userId}/status | status | 独立设置状态，避免用列表旧资料覆盖其他字段 |
| PUT /system/users/{userId}/password | newPassword、confirmPassword | 重置用户密码 |

`status` 为 `0` 正常、`1` 停用。搜索匹配账号、昵称与邮箱，不区分大小写。页码从1开始，每页1至100条。管理员仅查询本人和普通用户。

分页使用 MyBatis-Plus 的 `Page<SysUser>` / `IPage<SysUser>` 和全局 `PaginationInnerInterceptor`，数据库方言为 PostgreSQL。插件负责总数查询和分页 SQL，业务不再计算偏移量或手写 `LIMIT/OFFSET`、`COUNT(*)`。前端仍传 `pageNum`、`pageSize`，响应仍返回 `rows`、`total`、`pageNum`、`pageSize`；超出末页时返回空列表，由前端根据总数调整页码。

列表关联角色后分页，依赖005迁移的 `sys_user_role(user_id)` 唯一约束，保证一个用户至多一行。COUNT 关闭关联表移除优化，保留与列表相同的关联范围；关键词、角色、状态和管理员可见范围仍由同一份 SQL 筛选。若以后支持多角色，需先调整查询为按用户分页，不能直接在一对多关联结果上分页。

台账字段包括 userId、username、nickname、email、phone、avatar、role、roleName、status、createTime、updateTime、lastLoginAt 和操作权限字段。默认按创建时间与用户编号倒序。

账号为2至100个字符，允许文字、数字、下划线、点、@及连字符，数据库通过现有小写账号唯一索引处理大小写冲突，冲突返回409。新密码至少8个Unicode字符，最多72个UTF-8字节，以BCrypt保存，要求两次输入一致。修改密码后现有Token与数据库密码哈希不匹配，下次请求失效；角色权限在每次受保护请求时重新计算，停用账号不能登录或访问后台。

三种角色定义按标识匹配，不假定新增角色的编号。005迁移初始化角色与业务权限，后续业务写入可幂等补齐缺失角色定义，不覆盖已停用角色。`admin` 具有全部权限，`manager` 的7个权限包括普通用户维护及本人资料、密码修改，`common` 只有2个本人操作权限。具体权限通过角色—权限关联读取，接口使用 `@PreAuthorize` 检查权限，用户管理服务再校验目标范围。

数据库已移除旧 `sys_user.role` 字段，统一以 `sys_user_role` 为角色来源。关联表用唯一约束保证一个用户至多一个角色，延迟约束触发器在事务结束时保证每个未删除用户拥有一个有效角色，并保护内置超级管理员。服务仍可先插入账号再关联角色，或先移除旧角色再插入新角色；这些步骤必须处于同一事务。

数据库同时限制账号状态、删除标记、三种角色标识和BCrypt格式，保留账号大小写不敏感唯一索引，并为台账排序增加索引。密码的最少字符与字节数校验在应用处理明文时执行，数据库只保存和校验哈希格式。

## 本人账号接口

前端已移除独立“账号信息”页面，账号资料、头像和密码统一由管理员在用户管理中维护。以下本人账号接口保留供 API 调用，权限及数据范围不变。

- `PUT /account/profile`：username、nickname、email、avatar，所有已登录用户均仅能修改本人资料，保留手机号、状态及角色。
- `PUT /account/password`：currentPassword、newPassword、confirmPassword，先验证当前密码，拒绝与旧密码相同的新密码；成功后前端清除会话并重新登录。

资料修改后保留当前Token，后续请求读取新的账号资料。审计字段记录修改账号与时间。前端延续 `/api` 前缀和代理改写规则。

2026-10-15已实际执行数据库迁移并只读核对，见 [执行记录](database-migration-005.md)。未运行应用测试、构建或启动服务。

## 头像上传与 MinIO

用户管理的新增、编辑弹窗支持选择、手动裁剪、预览、更换与移除头像。选图后在浏览器内调整方形选区、缩放及90°旋转，同时预览方形与圆形效果；确认裁剪仅更新本地预览，保存用户时才上传。上传失败不会提交用户变更；图片上传成功而资料保存失败时，重试复用已上传路径。取消修改不改变数据库中的头像。

- `POST /files/avatars`：`multipart/form-data`，字段名 `file`；需登录且具有本人资料编辑或用户新增/编辑权限。返回 `data.avatar`，形如 `/files/avatars/<32位随机标识>.png`。上传不会直接修改用户记录，需在保存资料时提交返回路径。
- `GET /files/avatars/{name}`：按随机文件名公开读取头像图片，不提供目录、桶列表或任意对象读取。浏览器不需要传 JWT，MinIO 桶保持私有；头像链接本身可以分享。
- `avatar` 未传或为 `null` 时保留原头像，`""` 表示移除。仅接受本接口生成格式的路径，拒绝外部 URL 和任意对象键。沿用 `sys_user.avatar`，无需新数据库迁移；头像写入沿用现有事务、行锁与用户管理权限范围。
- 前端选图支持 JPG / JPEG / PNG / WebP / GIF / BMP / AVIF，原图最大2 MiB，边长不超过4096、总像素不超过1600万；格式须能被当前浏览器解码。浏览器将动图定格，按手动选区导出最大512×512的方形PNG，小图不强行放大，仅上传裁剪结果。
- 上传接口仍接收最大2 MiB的 JPG / PNG，由后端按实际内容解码，先检查最大边长4096及总像素不超过1600万，再居中裁剪、缩至最大512×512并重编码为PNG，移除原文件名和元数据。前端已裁为方形，因此后端的处理不会改变用户所选范围；旧客户端仍可按原方式上传JPG / PNG。
- 本次编辑期间保留未裁剪原图和已确认选区，可重新调整；关闭弹窗时释放。本系统仅在MinIO存储最终头像，重新裁剪历史头像时无法恢复之前已舍弃的原图区域。
- 文件位于私有桶 `website-avatars` 的 `avatars/` 前缀。首次上传自动创建缺失的桶；不改变现有桶的权限。替换/移除头像只更新引用，保留旧对象，避免并发保存、事务回滚或浏览器缓存导致图片损坏；清理旧对象前应核对数据库引用。

服务器通过环境变量配置：

```dotenv
MINIO_ENDPOINT=http://你的MinIO地址:9000
MINIO_ACCESS_KEY=后端使用的访问密钥
MINIO_SECRET_KEY=后端使用的密钥
MINIO_AVATAR_BUCKET=website-avatars
```

`MINIO_ENDPOINT` 填 S3 API 地址，不填9001控制台地址。本机开发还可在后端仓库根目录的 `.local/minio.yml` 设置同名变量；此文件已加入 Git 忽略且不会打进 JAR。从后端仓库根目录启动应用，以加载该本地配置。生产环境使用服务器环境变量，业务账号需具有此桶的读写权限；自动创建桶还需要建桶权限，也可由管理员提前建好。

前端将相对头像路径拼接到 `VITE_API_BASE_URL`（默认 `/api`），图片由后端代理返回，兼容 HTTPS 网站和单独的 API 域名，无需配置浏览器直接访问 MinIO 的 CORS。Nginx 的 `/api/` 路由沿用去前缀规则；为兼容项目图片上传，统一设置 `client_max_body_size 12m;`，头像接口仍限制2MiB。

验证命令（JDK25）：

```powershell
mvn -pl personal-website-admin -am test
# 显式连接 .local/minio.yml 中的存储，创建并清理本次测试自己的随机图片，不连接业务数据库。
mvn -pl personal-website-admin -am -Dtest=MinioAvatarIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false -Dminio.live=true test
```
