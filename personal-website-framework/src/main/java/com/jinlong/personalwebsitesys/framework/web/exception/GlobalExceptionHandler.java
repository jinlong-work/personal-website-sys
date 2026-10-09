package com.jinlong.personalwebsitesys.framework.web.exception;

import com.jinlong.personalwebsitesys.common.core.domain.ApiError;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.common.constant.LogConstants;
import com.jinlong.personalwebsitesys.common.utils.SafeLogUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> service(ServiceException ex) {
        MDC.put(LogConstants.ERROR_CODE, ex.code());
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed() {
        MDC.put(LogConstants.ERROR_CODE, "VALIDATION_ERROR");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "请求体格式错误，请使用有效的 JSON 并检查字段类型"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method() {
        MDC.put(LogConstants.ERROR_CODE, "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType() {
        MDC.put(LogConstants.ERROR_CODE, "UNSUPPORTED_MEDIA_TYPE");
        return ResponseEntity.status(415).body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "请使用 application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        MDC.put(LogConstants.ERROR_CODE, "INTERNAL_ERROR");
        log.error("API request failed{}", SafeLogUtils.stackTrace(ex));
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }

}
