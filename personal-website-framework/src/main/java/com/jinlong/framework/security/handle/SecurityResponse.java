package com.jinlong.framework.security.handle;

import com.jinlong.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import tools.jackson.databind.json.JsonMapper;

public final class SecurityResponse {
    private SecurityResponse() { }
    public static void write(HttpServletResponse response, JsonMapper mapper, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(mapper.writeValueAsString(AjaxResult.error(status, message)));
    }
}
