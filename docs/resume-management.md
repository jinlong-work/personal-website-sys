# 在线简历管理

后台“在线简历”维护首页公开资料。超级管理员与管理员按 `website:resume:list` / `website:resume:edit` 权限查看和编辑，普通用户没有入口及写入权限。首次初始化迁入旧首页的中英文内容，之后以数据库为准；重新执行迁移不会覆盖已编辑资料。

首页第一屏展示姓名、英文姓名、职业、补充身份、主张、简介和所在地；第二屏集中展示“关于我”、教育背景、工作经历和技术栈；项目展示在其后。邮箱和电话同步到联系区域。原有 PDF 简历文件继续独立提供预览与下载，本模块不自动改写 PDF。

## 编辑方式

- 中文为主要内容，可选维护英文。英文基本信息逐字段回退到中文，教育、工作、技术栈各类列表为空时整组使用中文。
- 教育经历最多12项，工作经历最多20项，技术分组最多12组；每组最多30项技术。支持增加、删除、上移、下移，数组顺序就是展示顺序。中文列表清空后不会补回初始内容。
- 教育与工作经历通过 Element Plus 弹窗新增、编辑，列表展示摘要。弹窗确认后才更新当前编辑列表，取消不改变已有内容；确认后仍需点击页面的“保存并更新首页”写入数据库。
- 日期支持年份 `2023` 或年月 `2024-11`，未知开始时间可留空；工作结束时间留空表示至今。结束时间不能早于开始时间。
- 日期使用 Element Plus 选择器，可切换“按年 / 按月”。已有仅年份的记录按年回显；切换到按月时重新选择月份，避免补造月份。选择器和表单会校验起止时间顺序，数据库格式保持不变。
- 编辑先保留在页面中，点击“保存并更新首页”后一次写入整份资料。取消、切换后台模块或离开页面时提醒未保存修改。保存失败保留编辑内容，版本冲突返回409，需重新加载后再编辑。
- 保存后刷新首页即可看到更新；页面无需再次发布静态文案。对外仅返回简历内容，不返回操作人、版本等管理信息。

## 初始化

沿用现有 PostgreSQL 开发配置及 `scripts/project-provision-requirements.txt` 的 psycopg、PyYAML 依赖：

```powershell
# 默认只读核对
python scripts/provision_resume.py
# 新增表及管理员权限，首次导入 sql/007_resume_seed.json
python scripts/provision_resume.py --apply
```

迁移文件 `sql/007_resume_management_postgresql.sql` 自带事务和迁移标识；权限表迁移前快照存于私有 schema `website_backup_20261019_resume`。新增单例表 `website_resume`，JSONB 保存中英文内容，MyBatis-Plus `BaseMapper` 读取、LambdaUpdateWrapper 按版本更新，写入前锁定并重新核对操作人账号状态及角色。

本次已初始化当前开发数据库，导入1段教育、2段工作、3组技术，超级管理员与管理员各2个权限。没有改动账号、密码及原有项目。已有管理员登录会话可能仍缓存旧权限，管理员重新登录后获得新权限。

## 接口

| 方法与路径 | 用途 |
| --- | --- |
| GET /public/resume | 匿名获取 `{ zh, en }`，Cache-Control: no-store |
| GET /system/resume | 读取中英文编辑内容、version、updateTime、canEdit |
| PUT /system/resume | `{ version, zh, en }`，原子保存并递增版本 |

每种语言内容含 `basic`、`education`、`experience`、`skills`；完整初始结构见 `sql/007_resume_seed.json`。使用既有 `code/msg/data` 响应，管理接口须登录并有对应权限；公共接口仅允许匿名读取。

部署需执行007迁移并更新前后端；本地重启后端即可加载接口。开发预览可设置 `VITE_API_PROXY_TARGET` 指向新后端。

## 验证

前端：`pnpm exec vitest run src/content/__tests__/resumeDefaults.spec.js`。

后端单元测试：`ResumeManagementServiceTest`。真实 PostgreSQL 集成测试 `ResumeIntegrationTest` 仅在环境变量 `RUN_RESUME_INTEGRATION=true` 时运行，写入自动回滚；验证 JSONB 保存、乐观锁冲突、公开读取及匿名写入拦截。请仅对开发/测试数据库开启。
