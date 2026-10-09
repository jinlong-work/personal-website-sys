package com.jinlong.personalwebsitesys.common.core.domain;

/** 通用成功响应，保留现有前端使用的 code/message/data 协议。 */
public record ApiResult<T>(String code, String message, T data) {
    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>("OK", message, data);
    }
}
