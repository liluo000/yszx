package com.yunshuzhixue.modules.iam.controller.dto;

import java.util.List;

/** 角色说明（A-IAM-10），只读 */
public record RoleView(String role, String description, List<String> permissions) {
}
