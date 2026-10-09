package com.jinlong.personalwebsitesys.common.utils;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 仅输出诊断结构，排除异常消息中的请求值、SQL 和数据库凭据。 */
public final class SafeLogUtils {
    private SafeLogUtils() {}

    public static String token(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        String bounded = value.substring(0, Math.min(value.length(), 200));
        return bounded.replaceAll("[^a-zA-Z0-9_./:{}*?\\-]", "_");
    }

    public static String stackTrace(Throwable error) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int cause = 0; current != null && cause < 8 && seen.add(current); cause++) {
            result.append(cause == 0 ? "\n" : "\nCaused by: ")
                    .append(current.getClass().getName()).append(" [message omitted]");
            if (current instanceof SQLException sql) {
                result.append(" SQLState=").append(token(sql.getSQLState()))
                        .append(" vendorCode=").append(sql.getErrorCode());
            }
            StackTraceElement[] frames = current.getStackTrace();
            for (int index = 0; index < Math.min(frames.length, 40); index++) {
                result.append("\n\tat ").append(frames[index]);
            }
            if (frames.length > 40) result.append("\n\t... remaining frames omitted");
            current = current.getCause();
        }
        return result.toString();
    }
}
