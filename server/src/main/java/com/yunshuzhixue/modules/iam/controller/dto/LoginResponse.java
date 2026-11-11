package com.yunshuzhixue.modules.iam.controller.dto;

/** A-IAM-01 登录的响应 */
public record LoginResponse(String token, boolean mustChangePassword, SysUserView user) {
}
