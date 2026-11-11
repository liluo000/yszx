package com.yunshuzhixue.modules.iam.controller.dto;

/** 新建账号的响应：临时密码只返回这一次 */
public record CreatedSysUserResponse(SysUserView user, String temporaryPassword) {
}
