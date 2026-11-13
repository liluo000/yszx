package com.yunshuzhixue.modules.analytics.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.analytics.service.WorkbenchService;
import com.yunshuzhixue.modules.analytics.service.WorkbenchService.WorkbenchView;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台（A-DSH-03）：后台全部角色，按角色只返回有权限的字段 */
@RestController
@RequestMapping("/api/v1/admin")
public class WorkbenchController {

    private final WorkbenchService service;
    private final AdminAccess access;

    public WorkbenchController(WorkbenchService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/workbench")
    public ApiResponse<WorkbenchView> workbench() {
        return ApiResponse.ok(service.load(access.require(AdminPermission.WORKBENCH).role()));
    }
}
