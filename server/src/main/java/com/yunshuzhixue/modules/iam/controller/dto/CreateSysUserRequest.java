package com.yunshuzhixue.modules.iam.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSysUserRequest(
        @NotBlank(message = "请输入账号") @Size(max = 50, message = "账号最多 50 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "账号只能包含字母、数字、下划线、点和短横线") String account,
        @NotBlank(message = "请输入姓名") @Size(max = 50, message = "姓名最多 50 个字符") String name,
        @NotBlank(message = "请输入手机号") @Pattern(regexp = "^1[0-9]{10}$", message = "手机号格式不正确") String phone,
        @NotBlank(message = "请选择角色")
        @Pattern(regexp = "^(super_admin|content_operator|student_operator)$", message = "角色不正确") String role) {
}
