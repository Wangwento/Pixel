package com.wwt.pixel.admin.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.pixel.admin.annotation.RequirePermission;
import com.wwt.pixel.common.constant.CommonConstant;
import com.wwt.pixel.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

public class AdminPermissionInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminPermissionInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            return true;
        }

        String adminIdStr = request.getHeader(CommonConstant.HEADER_ADMIN_ID);
        if (adminIdStr == null || adminIdStr.isEmpty()) {
            writeError(response, 401, "未登录");
            return false;
        }

        // super_admin 跳过权限校验
        Set<String> roles = redisTemplate.opsForSet().members("admin:roles:" + adminIdStr);
        if (roles != null && roles.contains("super_admin")) {
            return true;
        }

        // 校验权限码
        String requiredPermission = annotation.value();
        Set<String> permissions = redisTemplate.opsForSet().members("admin:permissions:" + adminIdStr);
        if (permissions == null || !permissions.contains(requiredPermission)) {
            writeError(response, 403, "无权限: " + requiredPermission);
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code == 401 ? 401 : 403);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
