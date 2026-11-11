package com.yunshuzhixue.modules.iam.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.iam.controller.dto.ChangeStatusRequest;
import com.yunshuzhixue.modules.iam.controller.dto.CreateSysUserRequest;
import com.yunshuzhixue.modules.iam.controller.dto.CreatedSysUserResponse;
import com.yunshuzhixue.modules.iam.controller.dto.PasswordResetResponse;
import com.yunshuzhixue.modules.iam.controller.dto.SysUserView;
import com.yunshuzhixue.modules.iam.controller.dto.UpdateSysUserRequest;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 系统用户（A-IAM-05~09），仅超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/sys-users")
public class SysUserController {

    private final SysUserService service;
    private final AdminAccess access;

    public SysUserController(SysUserService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<PageResult<SysUserView>> list(@RequestParam(required = false) Integer page,
                                                     @RequestParam(name = "page_size", required = false) Integer pageSize,
                                                     @RequestParam(required = false) String role,
                                                     @RequestParam(required = false) String status) {
        access.require(AdminPermission.SYS_USER);
        PageResult<SysUser> result = service.list(PageQuery.of(page, pageSize), role, status);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(SysUserView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedSysUserResponse> create(@Valid @RequestBody CreateSysUserRequest request) {
        CurrentAdmin operator = access.require(AdminPermission.SYS_USER);
        SysUserService.Created created = service.create(operator, request.account(), request.name(),
                request.phone(), request.role());
        return ApiResponse.ok(new CreatedSysUserResponse(SysUserView.of(created.user()), created.temporaryPassword()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SysUserView> update(@PathVariable Long id, @Valid @RequestBody UpdateSysUserRequest request) {
        CurrentAdmin operator = access.require(AdminPermission.SYS_USER);
        return ApiResponse.ok(SysUserView.of(service.update(operator, id, request.name(), request.phone(), request.role())));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<SysUserView> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        CurrentAdmin operator = access.require(AdminPermission.SYS_USER);
        return ApiResponse.ok(SysUserView.of(service.changeStatus(operator, id, request.status())));
    }

    @PostMapping("/{id}/password-resets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PasswordResetResponse> resetPassword(@PathVariable Long id) {
        CurrentAdmin operator = access.require(AdminPermission.SYS_USER);
        return ApiResponse.ok(new PasswordResetResponse(service.resetPassword(operator, id)));
    }
}
