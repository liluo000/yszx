package com.yunshuzhixue.modules.feedback.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.feedback.service.StudentFeedbackService;
import com.yunshuzhixue.modules.feedback.service.StudentFeedbackService.SubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学员反馈（S-FDB-01）：题目纠错与视频失效 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentFeedbackController {

    private final StudentFeedbackService service;

    public StudentFeedbackController(StudentFeedbackService service) {
        this.service = service;
    }

    @PostMapping("/feedbacks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackId> submit(@RequestBody SubmitRequest request) {
        StudentStp.LOGIC.checkLogin();
        long id = service.submit(StudentStp.LOGIC.getLoginIdAsLong(), request);
        return ApiResponse.ok(new FeedbackId(id));
    }

    public record FeedbackId(Long id) {
    }
}
