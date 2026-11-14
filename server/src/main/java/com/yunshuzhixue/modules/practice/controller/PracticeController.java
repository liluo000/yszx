package com.yunshuzhixue.modules.practice.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.practice.service.PracticeService;
import com.yunshuzhixue.modules.practice.service.PracticeService.AnswerDetail;
import com.yunshuzhixue.modules.practice.service.PracticeService.AnswerList;
import com.yunshuzhixue.modules.practice.service.PracticeService.PracticeRecord;
import com.yunshuzhixue.modules.practice.service.PracticeService.PracticeSessionView;
import com.yunshuzhixue.modules.practice.service.PracticeService.StartRequest;
import com.yunshuzhixue.modules.practice.service.PracticeService.SubmitRequest;
import com.yunshuzhixue.modules.practice.service.PracticeService.SubmitResult;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 刷题（S-PRC-01~05）：本批只支持按课程顺序练习 */
@RestController
@RequestMapping("/api/v1/student")
public class PracticeController {

    private final PracticeService service;

    public PracticeController(PracticeService service) {
        this.service = service;
    }

    @PostMapping("/practice-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PracticeSessionView> start(@RequestBody StartRequest request) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.start(studentId, request));
    }

    @GetMapping("/practice-sessions/{id}")
    public ApiResponse<PracticeSessionView> view(@PathVariable Long id) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.view(studentId, id));
    }

    @PostMapping("/practice-sessions/{id}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubmitResult> submit(@PathVariable Long id, @RequestBody SubmitRequest request) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.submit(studentId, id, request));
    }

    @GetMapping("/practice-sessions/{id}/answers")
    public ApiResponse<AnswerList> answers(@PathVariable Long id) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.answers(studentId, id));
    }

    @GetMapping("/practice-records")
    public ApiResponse<PageResult<PracticeRecord>> records(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "record_type", required = false) String recordType,
            @RequestParam(name = "learning_route_id", required = false) Long routeId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.records(studentId, PageQuery.of(page, pageSize), recordType,
                routeId, startDate, endDate));
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }
}
