package com.yunshuzhixue.modules.iam.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.AdminPrincipal;
import com.yunshuzhixue.common.web.RequireAdminPermission;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminStp;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台接口的登录校验与"必须先修改密码"拦截（04.API接口设计.md 2.7）。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String SESSION_MUST_CHANGE_PASSWORD = "mustChangePassword";

    private static final String LOGIN_PATH = "/api/v1/admin/sessions";

    private final AdminAccess access;

    public AdminAuthInterceptor(AdminAccess access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("OPTIONS".equals(method) || ("POST".equals(method) && LOGIN_PATH.equals(path))) {
            return true;
        }
        AdminStp.LOGIC.checkLogin();
        if (mustChangePassword() && !allowedWhileMustChange(method, path)) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        RequireAdminPermission required = requiredPermission(handler);
        if (required != null) {
            CurrentAdmin admin = access.requireAny(required.value());
            new AdminPrincipal(admin.id(), admin.name(), admin.role()).bind();
        }
        return true;
    }

    /** 方法上的注解优先于类上的注解 */
    private static RequireAdminPermission requiredPermission(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return null;
        }
        RequireAdminPermission onMethod = method.getMethodAnnotation(RequireAdminPermission.class);
        return onMethod != null ? onMethod : method.getBeanType().getAnnotation(RequireAdminPermission.class);
    }

    private boolean mustChangePassword() {
        Object flag = AdminStp.LOGIC.getSession().get(SESSION_MUST_CHANGE_PASSWORD);
        return Boolean.TRUE.equals(flag);
    }

    /** 必须修改密码时，只放行退出、查看当前账号、修改密码（A-IAM-02、03、04） */
    private boolean allowedWhileMustChange(String method, String path) {
        return ("DELETE".equals(method) && "/api/v1/admin/sessions/current".equals(path))
                || ("GET".equals(method) && "/api/v1/admin/me".equals(path))
                || ("PUT".equals(method) && "/api/v1/admin/me/password".equals(path));
    }
}
