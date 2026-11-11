package com.yunshuzhixue.modules.iam.controller.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 部分更新：只修改传入的字段 */
public record UpdateSysUserRequest(
        @Size(min = 1, max = 50, message = "姓名为 1~50 个字符") String name,
        @Pattern(regexp = "^1[0-9]{10}$", message = "手机号格式不正确") String phone,
        @Pattern(regexp = "^(super_admin|content_operator|student_operator)$", message = "角色不正确") String role) {
}
