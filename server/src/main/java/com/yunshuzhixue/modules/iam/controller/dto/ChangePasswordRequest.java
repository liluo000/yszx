package com.yunshuzhixue.modules.iam.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码") @Size(min = 6, max = 20, message = "密码长度为 6~20 位") String oldPassword,
        @NotBlank(message = "请输入新密码") @Size(min = 6, max = 20, message = "新密码长度为 6~20 位") String newPassword) {
}
