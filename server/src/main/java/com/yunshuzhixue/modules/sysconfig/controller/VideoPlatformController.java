package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.CreatePlatformRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.IdResponse;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.PlatformStatusRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.PlatformView;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.UpdatePlatformRequest;
import com.yunshuzhixue.modules.sysconfig.service.VideoPlatformService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 视频平台（A-CFG-09~12）。列表内容运营只读，用于课程表单选择平台 */
@RestController
@RequestMapping("/api/v1/admin/video-platforms")
public class VideoPlatformController {

    private final VideoPlatformService service;
    private final AdminAccess access;

    public VideoPlatformController(VideoPlatformService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<PlatformView>>> list() {
        access.requireAny(AdminPermission.CONFIG, AdminPermission.COURSE);
        return ApiResponse.ok(Map.of("items", service.list().stream().map(PlatformView::of).toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdResponse> create(@Valid @RequestBody CreatePlatformRequest request) {
        return ApiResponse.ok(new IdResponse(service.create(access.require(AdminPermission.CONFIG), request.name(),
                request.domains()).platform().getId()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdatePlatformRequest request) {
        service.update(access.require(AdminPermission.CONFIG), id, request.name(), request.domains());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody PlatformStatusRequest request) {
        service.changeStatus(access.require(AdminPermission.CONFIG), id, request.status());
        return ApiResponse.ok();
    }
}
