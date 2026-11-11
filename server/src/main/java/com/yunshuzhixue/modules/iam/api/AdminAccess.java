package com.yunshuzhixue.modules.iam.api;

/**
 * 后台接口的权限校验，供各模块的后台接口调用。每次都读取账号最新的角色和状态，角色修改后立即生效。
 */
public interface AdminAccess {

    /** 校验当前账号拥有该权限，否则抛出 403 FORBIDDEN */
    CurrentAdmin require(String permission);

    /** 校验当前账号拥有任一权限 */
    CurrentAdmin requireAny(String... permissions);

    /** 当前账号，不校验权限 */
    CurrentAdmin current();
}
