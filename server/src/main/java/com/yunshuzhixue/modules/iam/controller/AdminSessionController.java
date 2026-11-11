package com.yunshuzhixue.modules.iam.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.controller.dto.LoginRequest;
import com.yunshuzhixue.modules.iam.controller.dto.LoginResponse;
import com.yunshuzhixue.modules.iam.controller.dto.SysUserView;
import com.yunshuzhixue.modules.iam.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 后台登录与退出（A-IAM-01、A-IAM-02） */
@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

    private final AdminAuthService authService;

    public AdminSessionController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AdminAuthService.LoginResult result = authService.login(request.account(), request.password());
        return ApiResponse.ok(new LoginResponse(result.token(),
                Boolean.TRUE.equals(result.user().getMustChangePassword()), SysUserView.of(result.user())));
    }

    @DeleteMapping("/current")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }
}
