package com.jinlong.personalwebsitesys.common.core.domain;

/** 通用错误响应，不暴露内部异常或 SQL。 */
public record ApiError(String code, String message) {}
