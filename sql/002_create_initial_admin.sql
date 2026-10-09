-- 在已创建 public.sys_user 的同一个数据库中执行。
-- 修改下面 initial_password 的值后再运行；不要提交填写真实密码后的脚本。
-- 已存在同名账号时会报唯一约束冲突，不会覆盖已有密码或权限。

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    initial_username TEXT := 'admin';
    initial_password TEXT := 'REPLACE_WITH_YOUR_PASSWORD';
BEGIN
    IF initial_password = 'REPLACE_WITH_YOUR_PASSWORD' THEN
        RAISE EXCEPTION '请先将 initial_password 替换为你自己的管理员登录密码';
    END IF;
    IF OCTET_LENGTH(initial_password) = 0 OR OCTET_LENGTH(initial_password) > 72 THEN
        RAISE EXCEPTION '密码不能为空且UTF-8编码后不能超过72字节';
    END IF;

    INSERT INTO public.sys_user (username, password_hash, nickname, role, status)
    VALUES (initial_username, crypt(initial_password, gen_salt('bf', 12)), '曹进龙', 'ADMIN', 'ACTIVE');
END;
$$;

COMMIT;

-- 确认账号创建成功，不显示密码哈希。
SELECT id, username, nickname, role, status
FROM public.sys_user
WHERE LOWER(username) = LOWER('admin');
