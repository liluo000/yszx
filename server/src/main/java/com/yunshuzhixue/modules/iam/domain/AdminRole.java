package com.yunshuzhixue.modules.iam.domain;

import com.yunshuzhixue.modules.iam.api.AdminPermission;
import java.util.Arrays;
import java.util.List;

/**
 * 后台的三个固定角色及其权限（需求 2.2、6.10）。权限码与管理后台的菜单一一对应。
 */
public enum AdminRole {

    SUPER_ADMIN("super_admin", "超级管理员", AdminPermission.ALL),
    CONTENT_OPERATOR("content_operator", "内容运营", List.of(
            AdminPermission.WORKBENCH, AdminPermission.SUBJECT, AdminPermission.COURSE, AdminPermission.ROUTE,
            AdminPermission.PRACTICE_QUESTION, AdminPermission.INTERVIEW_QUESTION, AdminPermission.FEEDBACK)),
    STUDENT_OPERATOR("student_operator", "学员运营", List.of(
            AdminPermission.WORKBENCH, AdminPermission.STUDENT, AdminPermission.DASHBOARD));

    private final String value;
    private final String label;
    private final List<String> permissions;

    AdminRole(String value, String label, List<String> permissions) {
        this.value = value;
        this.label = label;
        this.permissions = permissions;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public List<String> permissions() {
        return permissions;
    }

    public static AdminRole of(String value) {
        return Arrays.stream(values()).filter(r -> r.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的后台角色：" + value));
    }
}
