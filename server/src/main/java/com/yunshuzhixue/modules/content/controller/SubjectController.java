package com.yunshuzhixue.modules.content.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.content.controller.dto.SubjectDtos.CreateSubjectRequest;
import com.yunshuzhixue.modules.content.controller.dto.SubjectDtos.IdResponse;
import com.yunshuzhixue.modules.content.controller.dto.SubjectDtos.SortRequest;
import com.yunshuzhixue.modules.content.controller.dto.SubjectDtos.SubjectView;
import com.yunshuzhixue.modules.content.controller.dto.SubjectDtos.UpdateSubjectRequest;
import com.yunshuzhixue.modules.content.service.SubjectService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学科管理（A-CNT-01~05）：内容运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/subjects")
public class SubjectController {

    private final SubjectService service;
    private final AdminAccess access;

    public SubjectController(SubjectService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<SubjectView>>> list() {
        access.require(AdminPermission.SUBJECT);
        return ApiResponse.ok(Map.of("items", service.list().stream().map(SubjectView::of).toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(new IdResponse(service.create(access.require(AdminPermission.SUBJECT), request.name(),
                request.intro(), request.sortNo()).getId()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest request) {
        service.update(access.require(AdminPermission.SUBJECT), id, request.name(), request.intro(), request.sortNo());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.SUBJECT), id);
        return ApiResponse.ok();
    }

    @PutMapping("/sort-order")
    public ApiResponse<Void> sort(@Valid @RequestBody SortRequest request) {
        service.sort(access.require(AdminPermission.SUBJECT), request.items().stream()
                .map(e -> new SubjectService.SortItem(e.id(), e.sortNo())).toList());
        return ApiResponse.ok();
    }
}
