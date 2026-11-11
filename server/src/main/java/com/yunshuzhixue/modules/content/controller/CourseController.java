package com.yunshuzhixue.modules.content.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.content.controller.dto.CourseDtos.CourseAdminView;
import com.yunshuzhixue.modules.content.controller.dto.CourseDtos.CourseStatusRequest;
import com.yunshuzhixue.modules.content.controller.dto.CourseDtos.CreateCourseRequest;
import com.yunshuzhixue.modules.content.controller.dto.CourseDtos.UpdateCourseRequest;
import com.yunshuzhixue.modules.content.service.CourseImportService;
import com.yunshuzhixue.modules.content.service.CourseService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
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

/** 课程库（A-CNT-06~12）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/courses")
public class CourseController {

    private final CourseService service;
    private final CourseImportService importService;
    private final AdminAccess access;

    public CourseController(CourseService service, CourseImportService importService, AdminAccess access) {
        this.service = service;
        this.importService = importService;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<PageResult<CourseAdminView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "has_questions", required = false) Boolean hasQuestions,
            @RequestParam(required = false) String keyword) {
        access.require(AdminPermission.COURSE);
        if (status != null && !status.isEmpty() && !status.matches("^(on_shelf|off_shelf)$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "状态不正确");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "关键词最多 100 个字符");
        }
        PageResult<CourseService.CourseItem> result =
                service.list(PageQuery.of(page, pageSize), subjectId, status, hasQuestions, keyword);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(CourseAdminView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseAdminView> create(@Valid @RequestBody CreateCourseRequest request) {
        return ApiResponse.ok(CourseAdminView.of(service.create(access.require(AdminPermission.COURSE), request.toFields())));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseAdminView> get(@PathVariable Long id) {
        access.require(AdminPermission.COURSE);
        return ApiResponse.ok(CourseAdminView.of(service.get(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CourseAdminView> update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return ApiResponse.ok(CourseAdminView.of(service.update(access.require(AdminPermission.COURSE), id,
                request.toFields())));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<CourseAdminView> changeStatus(@PathVariable Long id, @Valid @RequestBody CourseStatusRequest request) {
        return ApiResponse.ok(CourseAdminView.of(service.changeStatus(access.require(AdminPermission.COURSE), id,
                request.status())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.COURSE), id);
        return ApiResponse.ok();
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseImportService.ImportResult> importCourses(@RequestParam("file") MultipartFile file) {
        var operator = access.require(AdminPermission.COURSE);
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请上传 xlsx 格式的导入模板");
        }
        try {
            return ApiResponse.ok(importService.importCourses(operator, file.getInputStream()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件");
        }
    }
}
