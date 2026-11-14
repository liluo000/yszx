package com.yunshuzhixue.modules.wrongbook.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.wrongbook.service.WrongQuestionService;
import com.yunshuzhixue.modules.wrongbook.service.WrongQuestionService.WrongQuestionList;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 错题本（S-WRG-01、02）：学员登录后可用 */
@RestController
@RequestMapping("/api/v1/student")
public class WrongQuestionController {

    private final WrongQuestionService service;

    public WrongQuestionController(WrongQuestionService service) {
        this.service = service;
    }

    @GetMapping("/wrong-questions")
    public ApiResponse<WrongQuestionList> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "course_id", required = false) Long courseId,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(name = "question_type", required = false) String questionType,
            @RequestParam(name = "learning_route_id", required = false) Long routeId) {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(service.list(PageQuery.of(page, pageSize), courseId, subjectId, questionType, routeId));
    }

    @DeleteMapping("/wrong-questions/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        StudentStp.LOGIC.checkLogin();
        service.remove(id);
        return ApiResponse.ok();
    }
}
