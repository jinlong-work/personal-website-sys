-- 用户管理业务迁移；在003/004完成后的 PostgreSQL public schema 执行。
-- 原账号及BCrypt哈希保持不变；备份存放在同库私有schema，不导出凭据。
-- 整体事务提交，已成功执行时跳过；任何前置检查或校验失败都会回滚。
BEGIN;
SET LOCAL search_path TO public;
SET LOCAL lock_timeout = '8s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(536041, 5);

DO $migration$
DECLARE
    schema_snapshot JSONB;
    sequence_name TEXT;
    sequence_last BIGINT;
    sequence_called BOOLEAN;
    next_identity BIGINT;
    sequence_table RECORD;
    account RECORD;
    original_users BIGINT;
BEGIN
    IF to_regclass('website_backup_20261015_um.migration_info') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM website_backup_20261015_um.migration_info
                   WHERE migration_id = '005_user_management' AND status = 'applied') THEN
            RAISE NOTICE '005_user_management already applied; no changes made';
            RETURN;
        END IF;
        RAISE EXCEPTION 'Existing migration backup requires inspection before continuing';
    END IF;
    IF to_regnamespace('website_backup_20261015_um') IS NOT NULL THEN
        RAISE EXCEPTION 'Backup schema already exists; refusing to overwrite';
    END IF;

    LOCK TABLE public.sys_user, public.sys_role, public.sys_user_role,
               public.sys_menu, public.sys_role_menu IN ACCESS EXCLUSIVE MODE;

    IF NOT EXISTS (SELECT 1 FROM public.sys_user WHERE user_id = 1 AND status = '0' AND del_flag = '0') THEN
        RAISE EXCEPTION 'Active built-in super administrator is required';
    END IF;
    IF EXISTS (SELECT 1 FROM public.sys_role WHERE role_key NOT IN ('admin', 'manager', 'common')) THEN
        RAISE EXCEPTION 'Unknown roles must be reviewed before applying the three-role model';
    END IF;
    IF EXISTS (SELECT user_id FROM public.sys_user_role GROUP BY user_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Multiple-role accounts require an explicit role decision before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM public.sys_user WHERE status NOT IN ('0', '1') OR del_flag NOT IN ('0', '2')
               OR user_name <> btrim(user_name) OR char_length(user_name) NOT BETWEEN 2 AND 100) THEN
        RAISE EXCEPTION 'Invalid account values require review; no account will be silently changed';
    END IF;
    IF EXISTS (SELECT 1 FROM public.sys_user
               WHERE password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$') THEN
        RAISE EXCEPTION 'An account has a non-BCrypt password; migration will not overwrite credentials';
    END IF;

    SELECT COUNT(*) INTO original_users FROM public.sys_user;
    SELECT jsonb_build_object(
        'columns', (SELECT jsonb_agg(to_jsonb(c)) FROM (
            SELECT table_name, column_name, data_type, character_maximum_length,
                   is_nullable, column_default, is_identity
            FROM information_schema.columns WHERE table_schema = 'public'
              AND table_name IN ('sys_user', 'sys_role', 'sys_user_role', 'sys_menu', 'sys_role_menu')
            ORDER BY table_name, ordinal_position
        ) c),
        'constraints', (SELECT jsonb_agg(to_jsonb(c)) FROM (
            SELECT conrelid::regclass::text AS table_name, conname, pg_get_constraintdef(oid) AS definition
            FROM pg_constraint WHERE conrelid IN ('public.sys_user'::regclass, 'public.sys_role'::regclass,
                'public.sys_user_role'::regclass, 'public.sys_menu'::regclass, 'public.sys_role_menu'::regclass)
            ORDER BY conrelid, conname
        ) c),
        'indexes', (SELECT jsonb_agg(to_jsonb(i)) FROM (
            SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'
                AND tablename IN ('sys_user', 'sys_role', 'sys_user_role', 'sys_menu', 'sys_role_menu')
            ORDER BY tablename, indexname
        ) i)
    ) INTO schema_snapshot;

    CREATE SCHEMA website_backup_20261015_um;
    REVOKE ALL ON SCHEMA website_backup_20261015_um FROM PUBLIC;
    CREATE TABLE website_backup_20261015_um.sys_user_before AS TABLE public.sys_user;
    CREATE TABLE website_backup_20261015_um.sys_role_before AS TABLE public.sys_role;
    CREATE TABLE website_backup_20261015_um.sys_user_role_before AS TABLE public.sys_user_role;
    CREATE TABLE website_backup_20261015_um.sys_menu_before AS TABLE public.sys_menu;
    CREATE TABLE website_backup_20261015_um.sys_role_menu_before AS TABLE public.sys_role_menu;
    CREATE TABLE website_backup_20261015_um.migration_info (
        migration_id TEXT PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        status TEXT NOT NULL,
        schema_before JSONB NOT NULL,
        verification JSONB NOT NULL
    );
    REVOKE ALL ON ALL TABLES IN SCHEMA website_backup_20261015_um FROM PUBLIC;

    -- 使用事务型 RESTART 同步identity，不将已有序列向后移动。
    FOR sequence_table IN SELECT * FROM (VALUES
        ('sys_user', 'user_id'), ('sys_role', 'role_id'), ('sys_menu', 'menu_id')
    ) AS tables(table_name, id_column) LOOP
        sequence_name := pg_get_serial_sequence('public.' || sequence_table.table_name, sequence_table.id_column);
        IF sequence_name IS NULL THEN RAISE EXCEPTION 'Missing identity sequence'; END IF;
        EXECUTE format('SELECT last_value, is_called FROM %s', sequence_name) INTO sequence_last, sequence_called;
        EXECUTE format('SELECT COALESCE(MAX(%I), 0) + 1 FROM public.%I',
                       sequence_table.id_column, sequence_table.table_name) INTO next_identity;
        next_identity := greatest(next_identity, CASE WHEN sequence_called THEN sequence_last + 1 ELSE sequence_last END);
        EXECUTE format('ALTER SEQUENCE %s RESTART WITH %s', sequence_name, next_identity);
    END LOOP;

    INSERT INTO public.sys_role (role_name, role_key, role_sort, status, del_flag, create_by)
    VALUES ('超级管理员', 'admin', 1, '0', '0', 'migration:005'),
           ('管理员', 'manager', 2, '0', '0', 'migration:005'),
           ('普通用户', 'common', 3, '0', '0', 'migration:005')
    ON CONFLICT (role_key) DO UPDATE SET role_name = EXCLUDED.role_name, role_sort = EXCLUDED.role_sort,
        status = '0', del_flag = '0', update_by = 'migration:005', update_time = CURRENT_TIMESTAMP;

    -- 仅为缺失关系的存量账号补齐角色；不覆盖已有角色或提升普通账号权限。
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'sys_user' AND column_name = 'role') THEN
        EXECUTE $sql$
            INSERT INTO public.sys_user_role (user_id, role_id)
            SELECT u.user_id, r.role_id FROM public.sys_user u JOIN public.sys_role r
              ON r.role_key = CASE WHEN u.user_id = 1 THEN 'admin'
                                   WHEN u.role = 'ADMIN' THEN 'admin' ELSE 'common' END
            WHERE u.del_flag = '0' AND NOT EXISTS (
                SELECT 1 FROM public.sys_user_role ur WHERE ur.user_id = u.user_id)
        $sql$;
    ELSE
        INSERT INTO public.sys_user_role (user_id, role_id)
        SELECT u.user_id, r.role_id FROM public.sys_user u JOIN public.sys_role r
          ON r.role_key = CASE WHEN u.user_id = 1 THEN 'admin' ELSE 'common' END
        WHERE u.del_flag = '0' AND NOT EXISTS (SELECT 1 FROM public.sys_user_role ur WHERE ur.user_id = u.user_id);
    END IF;

    -- 废弃旧单列角色来源：原值已保存在私有备份表，业务统一读取关联表。
    ALTER TABLE public.sys_user DROP COLUMN IF EXISTS role;
    ALTER TABLE public.sys_user ALTER COLUMN status TYPE CHAR(1) USING status::CHAR(1);
    ALTER TABLE public.sys_user ALTER COLUMN status SET DEFAULT '0';
    UPDATE public.sys_user SET nick_name = '' WHERE nick_name IS NULL;
    ALTER TABLE public.sys_user ALTER COLUMN nick_name SET DEFAULT '';
    ALTER TABLE public.sys_user ALTER COLUMN nick_name SET NOT NULL;

    ALTER TABLE public.sys_user DROP CONSTRAINT IF EXISTS ck_sys_user_username;
    ALTER TABLE public.sys_user ADD CONSTRAINT ck_sys_user_username
        CHECK (user_name = btrim(user_name) AND char_length(user_name) BETWEEN 2 AND 100);
    ALTER TABLE public.sys_user DROP CONSTRAINT IF EXISTS ck_sys_user_password_hash;
    ALTER TABLE public.sys_user ADD CONSTRAINT ck_sys_user_password_hash
        CHECK (password ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$');
    ALTER TABLE public.sys_user ADD CONSTRAINT ck_website_user_status CHECK (status IN ('0', '1'));
    ALTER TABLE public.sys_user ADD CONSTRAINT ck_website_user_deleted CHECK (del_flag IN ('0', '2'));
    ALTER TABLE public.sys_user ADD CONSTRAINT ck_website_builtin_admin
        CHECK (user_id <> 1 OR (status = '0' AND del_flag = '0'));
    ALTER TABLE public.sys_role ADD CONSTRAINT ck_website_role_key CHECK (role_key IN ('admin', 'manager', 'common'));
    ALTER TABLE public.sys_role ADD CONSTRAINT ck_website_role_status CHECK (status IN ('0', '1'));
    ALTER TABLE public.sys_role ADD CONSTRAINT ck_website_role_deleted CHECK (del_flag IN ('0', '2'));
    ALTER TABLE public.sys_user_role ADD CONSTRAINT uq_website_single_user_role UNIQUE (user_id);

    -- 保留标准账号唯一索引，清理旧迁移留下的同表达式重复索引。
    CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_user_name_lower ON public.sys_user (LOWER(user_name));
    DROP INDEX IF EXISTS public.uk_sys_user_username_lower;
    CREATE INDEX IF NOT EXISTS idx_sys_user_ledger ON public.sys_user (create_time DESC, user_id DESC)
        WHERE del_flag = '0';

    -- F类型仅代表已实现接口的权限点，不生成未实现的菜单路由。
    INSERT INTO public.sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, perms, create_by, remark)
    SELECT permission.name, 0, permission.position, '', 'F', '1', '0', permission.key, 'migration:005', '个人网站用户管理业务权限'
    FROM (VALUES
        (1, '用户台账查询', 'system:user:list'),
        (2, '新增用户', 'system:user:add'),
        (3, '编辑用户账号', 'system:user:edit'),
        (4, '重置用户密码', 'system:user:resetPwd'),
        (5, '设置用户角色', 'system:user:assignRole'),
        (6, '启停用户账号', 'system:user:changeStatus'),
        (7, '修改本人资料', 'account:profile:edit'),
        (8, '修改本人密码', 'account:password:edit')
    ) AS permission(position, name, key)
    WHERE NOT EXISTS (SELECT 1 FROM public.sys_menu m WHERE m.perms = permission.key);

    INSERT INTO public.sys_role_menu (role_id, menu_id)
    SELECT r.role_id, m.menu_id FROM public.sys_role r CROSS JOIN public.sys_menu m
    WHERE m.perms IN ('system:user:list', 'system:user:add', 'system:user:edit', 'system:user:resetPwd',
                      'system:user:assignRole', 'system:user:changeStatus', 'account:profile:edit', 'account:password:edit')
      AND (r.role_key = 'admin'
        OR (r.role_key = 'manager' AND m.perms <> 'system:user:assignRole')
        OR (r.role_key = 'common' AND m.perms IN ('account:profile:edit', 'account:password:edit')))
    ON CONFLICT DO NOTHING;

    -- 延迟到事务结束校验“至少一个角色”，允许服务先插账号再关联角色、先删旧角色再加新角色。
    CREATE OR REPLACE FUNCTION public.website_assert_user_role(account_id BIGINT) RETURNS VOID
    LANGUAGE plpgsql AS $function$
    DECLARE
        deleted CHAR(1);
        role_count BIGINT;
        active_role_count BIGINT;
        assigned_role TEXT;
    BEGIN
        SELECT del_flag INTO deleted FROM public.sys_user WHERE user_id = account_id;
        IF NOT FOUND THEN
            IF account_id = 1 THEN RAISE EXCEPTION 'Built-in super administrator cannot be deleted'; END IF;
            RETURN;
        END IF;
        IF deleted <> '0' THEN RETURN; END IF;
        SELECT COUNT(*), COUNT(*) FILTER (WHERE r.status = '0' AND r.del_flag = '0'), MIN(r.role_key)
          INTO role_count, active_role_count, assigned_role
        FROM public.sys_user_role ur LEFT JOIN public.sys_role r ON r.role_id = ur.role_id
        WHERE ur.user_id = account_id;
        IF role_count <> 1 OR active_role_count <> 1 THEN
            RAISE EXCEPTION 'A non-deleted account must have exactly one active role';
        END IF;
        IF account_id = 1 AND assigned_role <> 'admin' THEN
            RAISE EXCEPTION 'Built-in super administrator role cannot be changed';
        END IF;
    END;
    $function$;

    CREATE OR REPLACE FUNCTION public.website_check_user_role_change() RETURNS TRIGGER
    LANGUAGE plpgsql AS $function$
    BEGIN
        IF TG_OP <> 'INSERT' THEN PERFORM public.website_assert_user_role(OLD.user_id); END IF;
        IF TG_OP <> 'DELETE' THEN
            IF TG_OP = 'INSERT' THEN
                PERFORM public.website_assert_user_role(NEW.user_id);
            ELSIF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
                PERFORM public.website_assert_user_role(NEW.user_id);
            END IF;
        END IF;
        RETURN NULL;
    END;
    $function$;

    CREATE OR REPLACE FUNCTION public.website_check_role_definition_change() RETURNS TRIGGER
    LANGUAGE plpgsql AS $function$
    DECLARE affected RECORD;
    BEGIN
        FOR affected IN SELECT user_id FROM public.sys_user_role
                        WHERE role_id = OLD.role_id OR role_id = NEW.role_id LOOP
            PERFORM public.website_assert_user_role(affected.user_id);
        END LOOP;
        RETURN NULL;
    END;
    $function$;

    CREATE CONSTRAINT TRIGGER trg_website_user_role_required
        AFTER INSERT OR UPDATE OR DELETE ON public.sys_user
        DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.website_check_user_role_change();
    CREATE CONSTRAINT TRIGGER trg_website_user_single_role
        AFTER INSERT OR UPDATE OR DELETE ON public.sys_user_role
        DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.website_check_user_role_change();
    CREATE CONSTRAINT TRIGGER trg_website_role_definition
        AFTER UPDATE ON public.sys_role
        DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.website_check_role_definition_change();

    FOR account IN SELECT user_id FROM public.sys_user LOOP
        PERFORM public.website_assert_user_role(account.user_id);
    END LOOP;
    IF (SELECT COUNT(*) FROM public.sys_user) <> original_users OR EXISTS (
        SELECT 1 FROM website_backup_20261015_um.sys_user_before b
        LEFT JOIN public.sys_user u ON u.user_id = b.user_id
        WHERE u.user_id IS NULL OR u.user_name IS DISTINCT FROM b.user_name OR u.password IS DISTINCT FROM b.password
    ) THEN
        RAISE EXCEPTION 'Account identity or password changed unexpectedly; aborting migration';
    END IF;

    COMMENT ON TABLE public.sys_user_role IS '用户与角色一对一关联；事务提交时要求每个未删除账号拥有一个有效角色';
    COMMENT ON COLUMN public.sys_user.password IS '仅保存BCrypt哈希；现有密码在迁移时不重置';
    COMMENT ON COLUMN public.sys_user.status IS '0正常，1停用；内置超级管理员不可停用';
    COMMENT ON COLUMN public.sys_role.role_key IS 'admin超级管理员、manager管理员、common普通用户';
    COMMENT ON SCHEMA website_backup_20261015_um IS '005用户管理迁移前私有备份，含密码哈希，仅数据库拥有者可访问';
    REVOKE ALL ON FUNCTION public.website_assert_user_role(BIGINT) FROM PUBLIC;
    REVOKE ALL ON FUNCTION public.website_check_user_role_change() FROM PUBLIC;
    REVOKE ALL ON FUNCTION public.website_check_role_definition_change() FROM PUBLIC;

    INSERT INTO website_backup_20261015_um.migration_info (migration_id, status, schema_before, verification)
    VALUES ('005_user_management', 'applied', schema_snapshot, jsonb_build_object(
        'users_preserved', original_users, 'passwords_preserved', TRUE,
        'roles', (SELECT COUNT(*) FROM public.sys_role),
        'user_roles', (SELECT COUNT(*) FROM public.sys_user_role),
        'permissions', (SELECT COUNT(*) FROM public.sys_menu),
        'role_permissions', (SELECT COUNT(*) FROM public.sys_role_menu)
    ));
END;
$migration$;

-- 在提交前触发延迟约束；以下结果不包含密码或联系方式。
SET CONSTRAINTS ALL IMMEDIATE;
SELECT migration_id, status, applied_at, verification
FROM website_backup_20261015_um.migration_info WHERE migration_id = '005_user_management';
SELECT role_id, role_name, role_key, role_sort FROM public.sys_role ORDER BY role_sort;
COMMIT;
