package com.yunshuzhixue.modules.iam.api;

/** 当前登录的后台账号 */
public record CurrentAdmin(Long id, String account, String name, String role) {
}
