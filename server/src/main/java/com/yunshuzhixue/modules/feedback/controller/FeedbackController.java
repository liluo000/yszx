package com.yunshuzhixue.modules.feedback.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.feedback.controller.dto.FeedbackDtos.FeedbackItem;
import com.yunshuzhixue.modules.feedback.controller.dto.FeedbackDtos.HandleRequest;
import com.yunshuzhixue.modules.feedback.controller.dto.FeedbackDtos.HandleResponse;
import com.yunshuzhixue.modules.feedback.service.FeedbackService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 纠错与反馈（A-FDB-01、02）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin")
public class FeedbackController {

    private final FeedbackService service;
    private final AdminAccess access;

    public FeedbackController(FeedbackService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/feedbacks")
    public ApiResponse<PageResult<FeedbackItem>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "feedback_type", required = false) String feedbackType,
            @RequestParam(required = false) String status,
            @RequestParam(name = "course_id", required = false) Long courseId,
            @RequestParam(name = "error_type", required = false) String errorType,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        access.require(AdminPermission.FEEDBACK);
        check(feedbackType, "^(question_correction|video_invalid)$", "反馈类型不正确");
        check(status, "^(pending|corrected|rejected)$", "状态不正确");
        check(errorType, "^(stem_error|answer_error|analysis_error|other)$", "错误类型不正确");
        PageResult<FeedbackService.Group> result = service.list(PageQuery.of(page, pageSize), blankToNull(feedbackType),
                blankToNull(status), courseId, blankToNull(errorType), startDate, endDate);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(FeedbackItem::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping("/feedback-handlings")
    public ApiResponse<HandleResponse> handle(@Valid @RequestBody HandleRequest request) {
        return ApiResponse.ok(new HandleResponse(service.handle(access.require(AdminPermission.FEEDBACK),
                request.feedbackIds(), request.status(), request.handlingNote())));
    }

    private static void check(String value, String pattern, String message) {
        if (value != null && !value.isEmpty() && !value.matches(pattern)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
