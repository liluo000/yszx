package com.yunshuzhixue.modules.content.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.AddCoursesRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.SortRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.StageAdminView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.UpdateStageRequest;
import com.yunshuzhixue.modules.content.service.StageService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 路线阶段与阶段内课程（A-CNT-22、23、25~27）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/stages")
public class StageController {

    private final StageService service;
    private final AdminAccess access;

    public StageController(StageService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @PatchMapping("/{id}")
    public ApiResponse<StageAdminView> update(@PathVariable Long id, @Valid @RequestBody UpdateStageRequest request) {
        return ApiResponse.ok(StageAdminView.of(service.update(access.require(AdminPermission.ROUTE), id,
                request.name(), request.intro(), request.sortNo())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.ROUTE), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StageAdminView> addCourses(@PathVariable Long id, @Valid @RequestBody AddCoursesRequest request) {
        return ApiResponse.ok(StageAdminView.of(service.addCourses(access.require(AdminPermission.ROUTE), id,
                request.courseIds())));
    }

    @DeleteMapping("/{id}/courses/{courseId}")
    public ApiResponse<Void> removeCourse(@PathVariable Long id, @PathVariable Long courseId) {
        service.removeCourse(access.require(AdminPermission.ROUTE), id, courseId);
        return ApiResponse.ok();
    }

    /** items 中的 id 为课程 ID */
    @PutMapping("/{id}/courses/sort-order")
    public ApiResponse<Void> sortCourses(@PathVariable Long id, @Valid @RequestBody SortRequest request) {
        service.sortCourses(access.require(AdminPermission.ROUTE), id, request.toItems());
        return ApiResponse.ok();
    }
}
