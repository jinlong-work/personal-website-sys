package com.jinlong.personalwebsitesys.framework.web.filter;

import com.jinlong.personalwebsitesys.common.constant.LogConstants;
import com.jinlong.personalwebsitesys.common.utils.SafeLogUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** API 同步请求日志与关键操作日志；不读取请求体、查询参数、Cookie 或响应体。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger accessLog = LoggerFactory.getLogger("audit.access");
    private static final Logger operationLog = LoggerFactory.getLogger("audit.operation");
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        long started = System.nanoTime();
        boolean failed = false;
        try {
            MDC.clear();
            // 不接受客户端提供的日志标识，防止日志注入和标识冲突。
            String requestId = UUID.randomUUID().toString();
            MDC.put(LogConstants.REQUEST_ID, requestId);
            response.setHeader(LogConstants.REQUEST_ID_HEADER, requestId);
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            MDC.put(LogConstants.ERROR_CODE, "UNHANDLED_ERROR");
            log.error("Unhandled request exception{}", SafeLogUtils.stackTrace(ex));
            throw ex;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                // 只记录路由模板，不记录可能含敏感值的原始 URL 或查询字符串。
                Object mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = mapping == null ? "unmapped" : SafeLogUtils.token(mapping.toString());
                String code = MDC.get(LogConstants.ERROR_CODE);
                if (code == null) code = status < 400 ? "OK" : "HTTP_" + status;
                String detail = "method=" + SafeLogUtils.token(request.getMethod()) + " route=" + route
                        + " status=" + status + " durationMs=" + elapsed
                        + " ip=" + SafeLogUtils.token(request.getRemoteAddr())
                        + " code=" + SafeLogUtils.token(code);
                if (status >= 500) accessLog.error("request {}", detail);
                else if (status >= 400) accessLog.warn("request {}", detail);
                else accessLog.info("request {}", detail);

                Object operationAttribute = request.getAttribute(LogConstants.OPERATION_ATTRIBUTE);
                if (operationAttribute instanceof String operationName) {
                    String operation = SafeLogUtils.token(operationName);
                    String outcome = status >= 200 && status < 300 ? "SUCCESS" : "FAILURE";
                    if ("SUCCESS".equals(outcome)) operationLog.info("operation={} outcome={} {}", operation, outcome, detail);
                    else operationLog.warn("operation={} outcome={} {}", operation, outcome, detail);
                }
            } finally {
                // 线程池复用前恢复上下文，避免下一请求串用用户或追踪标识。
                MDC.clear();
                if (previous != null) MDC.setContextMap(previous);
            }
        }
    }

}
