package com.yunshuzhixue.modules.iam.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.controller.dto.ChangePasswordRequest;
import com.yunshuzhixue.modules.iam.controller.dto.MeResponse;
import com.yunshuzhixue.modules.iam.controller.dto.SysUserView;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前后台账号（A-IAM-03、A-IAM-04） */
@RestController
@RequestMapping("/api/v1/admin/me")
public class AdminMeController {

    private final AdminAuthService authService;

    public AdminMeController(AdminAuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<MeResponse> me() {
        SysUser user = authService.currentUser();
        return ApiResponse.ok(new MeResponse(SysUserView.of(user), Boolean.TRUE.equals(user.getMustChangePassword()),
                authService.permissionsOf(user)));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.oldPassword(), request.newPassword());
        return ApiResponse.ok();
    }
}
