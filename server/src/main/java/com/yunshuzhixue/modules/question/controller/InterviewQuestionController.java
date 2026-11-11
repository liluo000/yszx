package com.yunshuzhixue.modules.question.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.CreateInterviewRequest;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.InterviewQuestionView;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.StatusRequest;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.UpdateInterviewRequest;
import com.yunshuzhixue.modules.question.service.InterviewQuestionService;
import com.yunshuzhixue.modules.question.service.QuestionImportService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 面试题库（A-QST-08~14）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/interview-questions")
public class InterviewQuestionController {

    private final InterviewQuestionService service;
    private final QuestionImportService importService;
    private final AdminAccess access;

    public InterviewQuestionController(InterviewQuestionService service, QuestionImportService importService,
                                       AdminAccess access) {
        this.service = service;
        this.importService = importService;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<PageResult<InterviewQuestionView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(name = "course_id", required = false) Long courseId,
            @RequestParam(required = false) String status) {
        access.require(AdminPermission.INTERVIEW_QUESTION);
        QuestionRequests.checkStatus(status);
        PageResult<InterviewQuestionService.Item> result =
                service.list(PageQuery.of(page, pageSize), subjectId, courseId, status);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(InterviewQuestionView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewQuestionView> create(@Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(InterviewQuestionView.of(service.create(access.require(AdminPermission.INTERVIEW_QUESTION),
                request.toFields())));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewQuestionView> get(@PathVariable Long id) {
        access.require(AdminPermission.INTERVIEW_QUESTION);
        return ApiResponse.ok(InterviewQuestionView.of(service.get(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<InterviewQuestionView> update(@PathVariable Long id, @Valid @RequestBody UpdateInterviewRequest request) {
        return ApiResponse.ok(InterviewQuestionView.of(service.update(access.require(AdminPermission.INTERVIEW_QUESTION),
                id, request.toFields())));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<InterviewQuestionView> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(InterviewQuestionView.of(service.changeStatus(
                access.require(AdminPermission.INTERVIEW_QUESTION), id, request.status())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.INTERVIEW_QUESTION), id);
        return ApiResponse.ok();
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionImportService.ImportResult> importQuestions(@RequestParam("file") MultipartFile file)
            throws IOException {
        var operator = access.require(AdminPermission.INTERVIEW_QUESTION);
        QuestionRequests.checkXlsx(file);
        return ApiResponse.ok(importService.importInterview(operator, file.getInputStream()));
    }
}
