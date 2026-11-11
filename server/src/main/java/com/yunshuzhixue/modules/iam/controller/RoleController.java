package com.yunshuzhixue.modules.iam.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.controller.dto.RoleView;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.iam.domain.AdminRole;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理（A-IAM-10）：三个固定角色，只读 */
@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleController {

    private static final Map<AdminRole, String> DESCRIPTIONS = Map.of(
            AdminRole.SUPER_ADMIN, "拥有全部模块；操作日志、系统用户、角色管理、系统配置仅超级管理员可用",
            AdminRole.CONTENT_OPERATOR, "维护学科、课程库、学习路线、刷题题库、面试题库，处理纠错与反馈",
            AdminRole.STUDENT_OPERATOR, "管理学员（含导出名单），查看数据看板");

    private final AdminAccess access;

    public RoleController(AdminAccess access) {
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<RoleView>>> list() {
        access.require(AdminPermission.ROLE);
        List<RoleView> items = Arrays.stream(AdminRole.values())
                .map(r -> new RoleView(r.value(), DESCRIPTIONS.get(r), r.permissions()))
                .toList();
        return ApiResponse.ok(Map.of("items", items));
    }
}
