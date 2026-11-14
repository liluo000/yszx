package com.yunshuzhixue.modules.quota.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi.QuotaView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当日 AI 剩余次数（S-QTA-01） */
@RestController
@RequestMapping("/api/v1/student")
public class StudentQuotaController {

    private final AiQuotaApi quota;

    public StudentQuotaController(AiQuotaApi quota) {
        this.quota = quota;
    }

    @GetMapping("/ai-quota")
    public ApiResponse<QuotaView> current() {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(quota.current(StudentStp.LOGIC.getLoginIdAsLong()));
    }
}
