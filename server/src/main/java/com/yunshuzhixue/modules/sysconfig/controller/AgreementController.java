package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.AgreementView;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.PublishAgreementRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.PublishAgreementResponse;
import com.yunshuzhixue.modules.sysconfig.service.AgreementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 协议文本（A-CFG-03、04），仅超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/agreements")
public class AgreementController {

    private final AgreementService service;
    private final AdminAccess access;

    public AgreementController(AgreementService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<AgreementView>>> list() {
        access.require(AdminPermission.CONFIG);
        return ApiResponse.ok(Map.of("items", AgreementService.TYPES.stream()
                .map(type -> AgreementView.of(type, service.latest(type))).toList()));
    }

    @PostMapping("/{agreementType}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PublishAgreementResponse> publish(@PathVariable String agreementType,
                                                         @Valid @RequestBody PublishAgreementRequest request) {
        return ApiResponse.ok(new PublishAgreementResponse(
                service.publish(access.require(AdminPermission.CONFIG), agreementType, request.content()).getVersionNo()));
    }
}
