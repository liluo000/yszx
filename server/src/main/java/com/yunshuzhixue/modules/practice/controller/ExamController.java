package com.yunshuzhixue.modules.practice.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.practice.service.ExamService;
import com.yunshuzhixue.modules.practice.service.ExamService.AnswerSaved;
import com.yunshuzhixue.modules.practice.service.ExamService.DeviceClaim;
import com.yunshuzhixue.modules.practice.service.ExamService.ExamView;
import com.yunshuzhixue.modules.practice.service.ExamService.InProgressView;
import com.yunshuzhixue.modules.practice.service.ExamService.StartExamRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 模拟考试（S-EXM-01~08）：开考、继续、设备接管、逐题保存、交卷、放弃与重新评分 */
@RestController
@RequestMapping("/api/v1/student/exams")
public class ExamController {

    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    /** S-EXM-01 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamView> start(@RequestBody StartExamRequest request) {
        return ApiResponse.ok(service.start(loggedIn(), request));
    }

    /** S-EXM-02 */
    @GetMapping("/in-progress")
    public ApiResponse<InProgressView> inProgress() {
        return ApiResponse.ok(service.inProgress(loggedIn()));
    }

    /** S-EXM-03 */
    @GetMapping("/{examSessionId}")
    public ApiResponse<ExamView> view(@PathVariable Long examSessionId) {
        return ApiResponse.ok(service.view(loggedIn(), examSessionId));
    }

    /** S-EXM-04 */
    @PostMapping("/{examSessionId}/device-claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceClaim> claimDevice(@PathVariable Long examSessionId) {
        return ApiResponse.ok(service.claimDevice(loggedIn(), examSessionId));
    }

    /** S-EXM-05 */
    @PutMapping("/{examSessionId}/answers/{seqNo}")
    public ApiResponse<AnswerSaved> saveAnswer(@PathVariable Long examSessionId,
                                               @PathVariable int seqNo,
                                               @RequestBody(required = false) SaveAnswerRequest request,
                                               @RequestHeader(name = "X-Device-Claim", required = false) String claim) {
        return ApiResponse.ok(service.saveAnswer(loggedIn(), examSessionId, seqNo,
                request == null ? null : request.studentAnswer(), claim));
    }

    /** S-EXM-06 */
    @PostMapping("/{examSessionId}/submission")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamView> submit(@PathVariable Long examSessionId,
                                        @RequestHeader(name = "X-Device-Claim", required = false) String claim) {
        return ApiResponse.ok(service.submit(loggedIn(), examSessionId, claim));
    }

    /** S-EXM-07 */
    @PostMapping("/{examSessionId}/abandonment")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> abandon(@PathVariable Long examSessionId,
                                     @RequestHeader(name = "X-Device-Claim", required = false) String claim) {
        service.abandon(loggedIn(), examSessionId, claim);
        return ApiResponse.ok();
    }

    /** S-EXM-08 */
    @PostMapping("/{examSessionId}/rescoring")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamView> rescore(@PathVariable Long examSessionId) {
        return ApiResponse.ok(service.rescore(loggedIn(), examSessionId));
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }

    public record SaveAnswerRequest(Object studentAnswer) {
    }
}
