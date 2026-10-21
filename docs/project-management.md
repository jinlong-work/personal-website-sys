# 首页项目管理

后台“项目管理”维护首页作品。超级管理员和管理员可按分配权限查询、新增、编辑、删除、上传效果图及发布/下架项目；普通用户没有此入口。首页匿名读取已发布且未删除的项目，按排序值、项目编号升序展示。

## 已完成的数据配置

2026-10-16，已对当前开发配置连接的 PostgreSQL 和 MinIO 实际执行初始化：

| 项目 | 结果 |
| --- | --- |
| 业务表 | `public.website_project`、`public.website_project_asset` |
| 初始内容 | 8个项目全部迁入，保留原编号；8个已发布、3个精选 |
| 效果图 | 27张，按原比例转换成JPEG后存入MinIO；原站静态图片保留 |
| 项目图片桶 | `website-projects`，私有桶；对象前缀`projects/` |
| 操作权限 | 6个权限点；超级管理员、管理员各6个，普通用户未授权 |
| 账号 | 现有2个账号保持不变，迁移不修改账号、密码或角色归属 |
| 备份 | `website_backup_20261016_projects`，保存权限表迁移前快照与迁移标识；撤销PUBLIC访问权限 |

提交后使用新的只读连接核对：项目8个、已发布8个、精选3个，图片资源27条，MinIO对象27个，无缺失图片记录或存储对象，桶未授予匿名访问。`.local/minio.yml` 已补充 `MINIO_PROJECT_BUCKET: website-projects`，沿用现有MinIO连接与凭据，不将密钥写入源码。

## 项目内容

- 基本信息：项目名称、类型、详细介绍、技术栈（最多20项）、项目贡献（最多30项，每项最多2000字符）。
- 首页卡片：展示分类、标题、简介；留空分别使用项目类型、名称、详细介绍。
- 英文内容：可单独填写同类字段，留空时使用中文，数据库中的内容不会再被旧前端按项目编号的固定翻译覆盖。
- 展示控制：精选/更多项目、排序值、草稿/已发布。发布前必须有介绍和至少一张效果图。
- 图片图集：最多12张，可预览、前移/后移、设为封面及移除；第一张是首页封面。

项目写入使用MyBatis-Plus `BaseMapper`和Lambda条件，列表使用`Page`及现有分页插件，每页最多100条。写操作重新校验当前角色与权限，锁定操作人账号；更新、删除和发布切换带`version`条件，冲突返回409，避免并发编辑覆盖。删除为逻辑删除，图片对象与资源记录保留，不在保存事务中物理删除共享图片。

## 接口

所有响应沿用`code`、`msg`、`data`。管理接口需要Bearer Token及对应的`website:project:*`权限。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /public/projects` | 匿名获取已发布项目数组，响应禁止缓存；空列表不会补回旧静态项目 |
| `GET /system/projects` | keyword、status、pageNum、pageSize；返回rows、total、canCreate、canUpload |
| `GET /system/projects/{id}` | 获取项目编辑数据 |
| `POST /system/projects` | 新增项目，默认由页面以草稿保存 |
| `PUT /system/projects/{id}` | 保存完整项目内容，携带version |
| `PUT /system/projects/{id}/status` | status为draft/published，携带version |
| `DELETE /system/projects/{id}?version=...` | 逻辑删除项目 |
| `POST /files/projects` | multipart字段file，上传JPEG/PNG；返回path、width、height、size |
| `GET /files/projects/{name}` | 按32位随机文件名公开读取JPEG图片，不提供桶列表或任意文件读取 |

保存字段为`name`、`type`、`description`、`category`、`headline`、`summary`、`technologies`、`achievements`、`images`、`english`、`featured`、`sortOrder`、`status`及更新时的`version`。数组字段均按数组提交，`english`为对应内容的对象。`images`只接受资源表中已登记的`/files/projects/<32位标识>.jpg`路径，拒绝任意外链。

## 图片与MinIO

页面支持JPG/JPEG、PNG、WebP、GIF、BMP、AVIF，每张原图最大10MiB，边长最大8192、总像素不超过3200万；格式须能被当前浏览器解码。浏览器修正照片方向并将动图定格，按原比例缩至最大边长2560后转换为JPEG，透明区域使用白色背景。上传接口再次验证文件内容与尺寸、重新编码，移除原始元数据。

选图和调整顺序只在本地生效；保存项目时逐张上传新图片。上传成功但保存失败时保留已上传路径，重试复用；取消编辑不修改数据库中的项目。移除图片或删除项目只移除引用，不立即删除对象，避免影响其他引用、缓存及并发保存。

MinIO使用既有`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`，新增可选`MINIO_PROJECT_BUCKET`（默认`website-projects`）。桶保持私有，浏览器经`/api/files/projects/...`访问后端代理，不直连MinIO；图片链接本身可分享，包括已上传但尚未发布项目的图片。服务器需允许应用账号读取、写入此桶；自动建桶时还需建桶权限。

全局multipart限制已调整为单文件10MiB、请求12MiB，头像接口自身仍限制2MiB。生产Nginx的`/api/`代理需设置`client_max_body_size 12m;`。本次未修改线上Nginx或发布包。

## 新环境初始化

先完成003/004（按需）/005用户与权限迁移。006为事务性、可重复执行的增量迁移，发现未登记的同名项目表时会停止，不覆盖已有数据。

```powershell
python -m pip install --target .local/project-tools --no-compile -r scripts/project-provision-requirements.txt
# 默认只读检查；连接来自环境变量及本机忽略配置。
python scripts/provision_projects.py
# 显式创建表、权限、私有桶，并从已保存的seed首次导入旧站内容及图片。
python scripts/provision_projects.py --apply --frontend-root ../personal-website
```

`sql/006_project_seed.json`保存初始中英文项目内容，`scripts/export-project-seed.mjs`用于从迁移前的静态配置生成它。初始化后以数据库为准，不用此脚本覆盖日常编辑内容；初始化脚本的seed标识会阻止重复导入。凭据仅从环境和忽略文件读取，命令行与执行记录不包含密钥。

## 编译与使用

本次前端构建通过，后端使用JDK25执行`mvn -o '-P!release' -pl personal-website-admin -am -DskipTests compile`通过；未运行单元测试、接口测试或浏览器交互测试。系统默认终端的`JAVA_HOME`仍指向JDK21，编译时仅在当前命令临时选择了已安装的JDK25，未修改全局设置。

重启后端与前端开发服务，再刷新或重新登录后台进入“项目管理”。生产发布需同时部署新前后端，并同步上传大小配置，旧静态构建不会自行读取新接口。
