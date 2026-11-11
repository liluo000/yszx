package com.yunshuzhixue.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在后台接口（类或方法）上，声明访问所需的权限码，满足任一即可。
 * 由 iam 模块的拦截器统一校验，业务模块不必为了鉴权依赖 iam，避免模块之间循环依赖。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireAdminPermission {

    String[] value();
}
