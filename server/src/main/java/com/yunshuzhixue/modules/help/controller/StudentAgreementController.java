package com.yunshuzhixue.modules.help.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.help.service.AgreementConsentService;
import com.yunshuzhixue.modules.help.service.StudentAgreementService;
import com.yunshuzhixue.modules.help.service.StudentAgreementService.AgreementDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 帮助与协议（S-HLP-01、02）：协议正文对游客公开，同意协议需要登录 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentAgreementController {

    private final StudentAgreementService agreements;
    private final AgreementConsentService consents;

    public StudentAgreementController(StudentAgreementService agreements, AgreementConsentService consents) {
        this.agreements = agreements;
        this.consents = consents;
    }

    @GetMapping("/agreements/{agreementType}")
    public ApiResponse<AgreementDetail> latest(@PathVariable String agreementType) {
        return ApiResponse.ok(agreements.latest(agreementType));
    }

    /** 协议更新后重新同意；注销中的账号也可调用 */
    @PostMapping("/agreement-consents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> consent(@Valid @RequestBody ConsentRequest request) {
        StudentStp.LOGIC.checkLogin();
        long studentId = StudentStp.LOGIC.getLoginIdAsLong();
        consents.accept(studentId, request.agreements().stream()
                .map(item -> new AgreementConsentService.Consent(item.agreementType(), item.versionNo()))
                .toList());
        return ApiResponse.ok();
    }

    public record ConsentRequest(@NotEmpty @Size(max = 2) @Valid List<AgreementRefRequest> agreements) {
    }

    public record AgreementRefRequest(@NotBlank String agreementType, int versionNo) {
    }
}
