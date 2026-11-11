package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.ConfigView;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.UpdateConfigRequest;
import com.yunshuzhixue.modules.sysconfig.service.ConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统配置项（A-CFG-01、02），仅超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/configs")
public class ConfigController {

    private final ConfigService service;
    private final AdminAccess access;

    public ConfigController(ConfigService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<ConfigView>>> list() {
        access.require(AdminPermission.CONFIG);
        return ApiResponse.ok(Map.of("items", service.list().stream().map(ConfigView::of).toList()));
    }

    @PutMapping("/{configKey}")
    public ApiResponse<Void> update(@PathVariable String configKey, @Valid @RequestBody UpdateConfigRequest request) {
        service.update(access.require(AdminPermission.CONFIG), configKey, request.configValue());
        return ApiResponse.ok();
    }
}
