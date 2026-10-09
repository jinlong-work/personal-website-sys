-- PostgreSQL：个人网站后台用户表
-- 在需要存放网站业务数据的数据库中执行。
-- 不包含初始账号；password_hash 必须由后端生成，不能填写明文密码。

BEGIN;

CREATE TABLE public.sys_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(100),
    email           VARCHAR(254),
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_sys_user_username
        CHECK (username = BTRIM(username) AND CHAR_LENGTH(username) > 0),
    CONSTRAINT ck_sys_user_password_hash
        CHECK (CHAR_LENGTH(BTRIM(password_hash)) > 0),
    CONSTRAINT ck_sys_user_role
        CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_sys_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 账号不区分大小写，避免 admin 与 Admin 注册为两个账号。
-- 后端查询账号时也应使用 LOWER(username) = LOWER(?)。
CREATE UNIQUE INDEX uk_sys_user_username_lower
    ON public.sys_user (LOWER(username));

-- 更新任意用户信息时自动维护更新时间。
CREATE FUNCTION public.sys_user_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sys_user_updated_at
    BEFORE UPDATE ON public.sys_user
    FOR EACH ROW
    EXECUTE FUNCTION public.sys_user_set_updated_at();

COMMENT ON TABLE public.sys_user IS '个人网站后台用户';
COMMENT ON COLUMN public.sys_user.id IS '用户主键，数据库自动生成';
COMMENT ON COLUMN public.sys_user.username IS '登录账号，唯一且不区分大小写';
COMMENT ON COLUMN public.sys_user.password_hash IS '后端生成的密码哈希（如 BCrypt 或 Argon2），禁止保存明文密码';
COMMENT ON COLUMN public.sys_user.nickname IS '显示名称';
COMMENT ON COLUMN public.sys_user.email IS '联系邮箱，可为空，不作为登录标识';
COMMENT ON COLUMN public.sys_user.role IS '角色：ADMIN 管理员，USER 普通用户';
COMMENT ON COLUMN public.sys_user.status IS '账号状态：ACTIVE 启用，DISABLED 停用';
COMMENT ON COLUMN public.sys_user.last_login_at IS '最近成功登录时间，由认证逻辑更新';
COMMENT ON COLUMN public.sys_user.created_at IS '创建时间';
COMMENT ON COLUMN public.sys_user.updated_at IS '最近更新时间，由触发器自动更新';

COMMIT;
