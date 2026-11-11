package com.yunshuzhixue.common.web;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 当前请求的后台账号。由 iam 的拦截器在校验 {@link RequireAdminPermission} 后放入请求，
 * 供 iam 依赖的模块（如 audit）在不依赖 iam 的情况下取得操作人，避免循环依赖。
 */
public record AdminPrincipal(Long id, String name, String role) {

    private static final String ATTRIBUTE = AdminPrincipal.class.getName();

    public void bind() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ATTRIBUTE, this, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /** 当前操作人；只在标注了 RequireAdminPermission 的接口中可用 */
    public static AdminPrincipal current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AdminPrincipal principal)) {
            throw new IllegalStateException("当前接口未经过后台权限校验");
        }
        return principal;
    }
}
