package com.yunshuzhixue.modules.iam.controller.dto;

/** 重置密码的响应：临时密码只返回这一次 */
public record PasswordResetResponse(String temporaryPassword) {
}
