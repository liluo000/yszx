package com.yunshuzhixue.modules.question.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.CreatePracticeRequest;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.PracticeQuestionView;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.StatusRequest;
import com.yunshuzhixue.modules.question.controller.dto.QuestionDtos.UpdatePracticeRequest;
import com.yunshuzhixue.modules.question.service.PracticeQuestionService;
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

/** 刷题题库（A-QST-01~07）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/practice-questions")
public class PracticeQuestionController {

    private final PracticeQuestionService service;
    private final QuestionImportService importService;
    private final AdminAccess access;

    public PracticeQuestionController(PracticeQuestionService service, QuestionImportService importService,
                                      AdminAccess access) {
        this.service = service;
        this.importService = importService;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<PageResult<PracticeQuestionView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(name = "course_id", required = false) Long courseId,
            @RequestParam(name = "question_type", required = false) String questionType,
            @RequestParam(required = false) String status) {
        access.require(AdminPermission.PRACTICE_QUESTION);
        QuestionRequests.checkStatus(status);
        if (questionType != null && !questionType.isEmpty()
                && !questionType.matches("^(true_false|single_choice|multiple_choice|short_answer)$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题型不正确");
        }
        PageResult<PracticeQuestionService.Item> result =
                service.list(PageQuery.of(page, pageSize), subjectId, courseId, questionType, status);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(PracticeQuestionView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PracticeQuestionView> create(@Valid @RequestBody CreatePracticeRequest request) {
        return ApiResponse.ok(PracticeQuestionView.of(service.create(access.require(AdminPermission.PRACTICE_QUESTION),
                request.toFields())));
    }

    @GetMapping("/{id}")
    public ApiResponse<PracticeQuestionView> get(@PathVariable Long id) {
        access.require(AdminPermission.PRACTICE_QUESTION);
        return ApiResponse.ok(PracticeQuestionView.of(service.get(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PracticeQuestionView> update(@PathVariable Long id, @Valid @RequestBody UpdatePracticeRequest request) {
        return ApiResponse.ok(PracticeQuestionView.of(service.update(access.require(AdminPermission.PRACTICE_QUESTION),
                id, request.toFields())));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<PracticeQuestionView> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(PracticeQuestionView.of(service.changeStatus(
                access.require(AdminPermission.PRACTICE_QUESTION), id, request.status())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.PRACTICE_QUESTION), id);
        return ApiResponse.ok();
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionImportService.ImportResult> importQuestions(@RequestParam("file") MultipartFile file)
            throws IOException {
        var operator = access.require(AdminPermission.PRACTICE_QUESTION);
        QuestionRequests.checkXlsx(file);
        return ApiResponse.ok(importService.importPractice(operator, file.getInputStream()));
    }
}
