-- 在线简历；仅新增单例内容和权限，不修改账号或已有项目。
BEGIN;
SET LOCAL search_path TO public;
SET LOCAL lock_timeout = '8s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(536041, 7);
CREATE SCHEMA IF NOT EXISTS website_backup_20261019_resume;
REVOKE ALL ON SCHEMA website_backup_20261019_resume FROM PUBLIC;
CREATE TABLE IF NOT EXISTS website_backup_20261019_resume.migration_info (
    migration_id TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DO $migration$
BEGIN
    IF EXISTS (SELECT 1 FROM website_backup_20261019_resume.migration_info WHERE migration_id = '007_resume_management') THEN
        RETURN;
    END IF;
    IF to_regclass('public.website_resume') IS NOT NULL THEN
        RAISE EXCEPTION 'Resume table exists without migration marker; refusing to overwrite';
    END IF;
    LOCK TABLE public.sys_menu, public.sys_role_menu IN SHARE ROW EXCLUSIVE MODE;
    CREATE TABLE website_backup_20261019_resume.sys_menu_before AS TABLE public.sys_menu;
    CREATE TABLE website_backup_20261019_resume.sys_role_menu_before AS TABLE public.sys_role_menu;
    CREATE TABLE public.website_resume (
        resume_id BIGINT PRIMARY KEY CHECK (resume_id = 1),
        content JSONB NOT NULL CHECK (jsonb_typeof(content) = 'object' AND content ? 'zh' AND content ? 'en'),
        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
        update_by VARCHAR(100) NOT NULL DEFAULT '',
        update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    COMMENT ON TABLE public.website_resume IS '首页在线简历：中英文基本资料、教育、工作和技术栈，数组顺序即展示顺序';
    INSERT INTO public.sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, perms, create_by, remark)
    SELECT permission.name, 0, permission.position, '', 'F', '1', '0', permission.key, 'migration:007', '个人网站在线简历管理权限'
    FROM (VALUES (30, '简历查询', 'website:resume:list'), (31, '编辑简历', 'website:resume:edit')) AS permission(position, name, key)
    WHERE NOT EXISTS (SELECT 1 FROM public.sys_menu m WHERE m.perms = permission.key);
    INSERT INTO public.sys_role_menu (role_id, menu_id)
    SELECT r.role_id, m.menu_id FROM public.sys_role r CROSS JOIN public.sys_menu m
    WHERE r.role_key IN ('admin', 'manager') AND r.status = '0' AND r.del_flag = '0'
      AND m.perms IN ('website:resume:list', 'website:resume:edit')
    ON CONFLICT DO NOTHING;
    INSERT INTO website_backup_20261019_resume.migration_info (migration_id) VALUES ('007_resume_management');
END
$migration$;
REVOKE ALL ON ALL TABLES IN SCHEMA website_backup_20261019_resume FROM PUBLIC;
COMMIT;
