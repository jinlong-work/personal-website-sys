package com.jinlong.personalwebsitesys.common.constant;

/** 日志上下文键，不使用用户名、密码或会话令牌作为追踪标识。 */
public final class LogConstants {
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String ERROR_CODE = "errorCode";
    public static final String OPERATION_ATTRIBUTE = "AUDIT_OPERATION";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    private LogConstants() {}
}
