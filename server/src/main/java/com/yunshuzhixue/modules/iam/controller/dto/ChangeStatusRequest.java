package com.yunshuzhixue.modules.iam.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(
        @NotBlank(message = "请选择状态") @Pattern(regexp = "^(enabled|disabled)$", message = "状态不正确") String status) {
}
