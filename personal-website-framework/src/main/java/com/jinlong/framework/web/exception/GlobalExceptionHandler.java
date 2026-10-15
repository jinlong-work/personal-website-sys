package com.jinlong.framework.web.exception;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AjaxResult> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(AjaxResult.error(413, "头像图片不能超过2MB"));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<AjaxResult> handleInvalidUpload(Exception exception) {
        return ResponseEntity.badRequest().body(AjaxResult.error(400, "请选择有效的头像图片"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AjaxResult> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(AjaxResult.error(404, "接口不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AjaxResult> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405).body(AjaxResult.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> handleServiceException(ServiceException exception) {
        int status = exception.getCode() == null ? 400 : exception.getCode();
        return ResponseEntity.status(status).body(AjaxResult.error(status, exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<AjaxResult> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(AjaxResult.error(400, "请求参数不正确"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AjaxResult> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(AjaxResult.error(403, "没有访问权限"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AjaxResult> handleDataAccess(DataAccessException exception) {
        log.error("数据库或缓存服务暂不可用", exception);
        return ResponseEntity.status(503).body(AjaxResult.error(503, "数据库或缓存服务暂不可用"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AjaxResult> handleException(Exception exception) {
        log.error("请求处理失败", exception);
        return ResponseEntity.internalServerError().body(AjaxResult.error(500, "系统异常，请稍后重试"));
    }
}
