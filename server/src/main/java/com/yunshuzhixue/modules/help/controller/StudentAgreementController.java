package com.yunshuzhixue.modules.help.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.help.service.StudentAgreementService;
import com.yunshuzhixue.modules.help.service.StudentAgreementService.AgreementDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 帮助与协议（S-HLP-01）：协议最新版本对游客公开 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentAgreementController {

    private final StudentAgreementService agreements;

    public StudentAgreementController(StudentAgreementService agreements) {
        this.agreements = agreements;
    }

    @GetMapping("/agreements/{agreementType}")
    public ApiResponse<AgreementDetail> latest(@PathVariable String agreementType) {
        return ApiResponse.ok(agreements.latest(agreementType));
    }
}
