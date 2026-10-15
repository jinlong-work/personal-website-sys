-- 先执行003；仅在没有超级管理员时执行，已有旧admin账号迁移后无需执行本文件。
-- 替换 initial_password；已存在ID=1或同名用户时直接报错，不覆盖已有账号。
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
DO $$
DECLARE
    initial_username TEXT := 'admin';
    initial_password TEXT := 'REPLACE_WITH_YOUR_PASSWORD';
BEGIN
    IF initial_password = 'REPLACE_WITH_YOUR_PASSWORD' THEN
        RAISE EXCEPTION '请先替换initial_password为自己的后台登录密码';
    END IF;
    IF CHAR_LENGTH(initial_password) < 5 OR OCTET_LENGTH(initial_password) > 72 THEN
        RAISE EXCEPTION '密码至少5个字符且UTF-8编码不超过72字节';
    END IF;
    IF EXISTS (SELECT 1 FROM public.sys_user WHERE user_id=1 OR LOWER(user_name)=LOWER(initial_username)) THEN
        RAISE EXCEPTION 'ID=1或同名账号已存在，请使用迁移后的原账号，勿重复初始化';
    END IF;
    INSERT INTO public.sys_user (user_id, dept_id, user_name, nick_name, password, status, del_flag, create_by)
    OVERRIDING SYSTEM VALUE
    VALUES (1, 100, initial_username, '管理员', crypt(initial_password, gen_salt('bf', 12)), '0', '0', 'admin');
    INSERT INTO public.sys_user_role (user_id, role_id)
    SELECT 1, role_id FROM public.sys_role WHERE role_key='admin';
END;
$$;
SELECT setval(pg_get_serial_sequence('public.sys_user','user_id'), GREATEST(MAX(user_id),1), true) FROM public.sys_user;
COMMIT;
SELECT user_id, user_name, nick_name, status FROM public.sys_user WHERE user_id=1;
