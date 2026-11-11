package com.yunshuzhixue.modules.iam.controller.dto;

import java.util.List;

/** A-IAM-03 当前账号和权限的响应 */
public record MeResponse(SysUserView user, boolean mustChangePassword, List<String> permissions) {
}
