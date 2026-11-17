package com.yunshuzhixue.modules.report.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.report.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 面试记录与报告（S-RPT-01~03）：与 M11 共用 /student/interviews 路径，按子路径区分 */
@RestController
@RequestMapping("/api/v1/student/interviews")
public class InterviewRecordController {

    private final ReportService reports;

    public InterviewRecordController(ReportService reports) {
        this.reports = reports;
    }

    /** S-RPT-01 面试记录列表 */
    @GetMapping
    public ApiResponse<PageResult<ReportService.InterviewListItem>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        return ApiResponse.ok(reports.list(loggedIn(), p, size));
    }

    /** S-RPT-02 查看面试报告；生成中返回 status=generating */
    @GetMapping("/{interviewSessionId}/report")
    public ApiResponse<ReportService.ReportView> view(@PathVariable long interviewSessionId) {
        ReportService.ReportView view = reports.view(loggedIn(), interviewSessionId);
        if (view == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "本场面试没有报告");
        }
        return ApiResponse.ok(view);
    }

    /** S-RPT-03 重新生成报告，仅失败状态可用 */
    @PostMapping("/{interviewSessionId}/report/regeneration")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportService.ReportView> regenerate(@PathVariable long interviewSessionId) {
        return ApiResponse.ok(reports.regenerate(loggedIn(), interviewSessionId));
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }
}
