package com.yunshuzhixue.modules.iam.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入账号") @Size(max = 50, message = "账号最多 50 个字符") String account,
        @NotBlank(message = "请输入密码") @Size(min = 6, max = 20, message = "密码长度为 6~20 位") String password) {
}
