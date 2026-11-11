package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.CreateFaqRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.FaqView;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.IdResponse;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.SortRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.UpdateFaqRequest;
import com.yunshuzhixue.modules.sysconfig.service.FaqService;
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

/** 常见问题（A-CFG-13~17），仅超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/faqs")
public class FaqController {

    private final FaqService service;
    private final AdminAccess access;

    public FaqController(FaqService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, List<FaqView>>> list() {
        access.require(AdminPermission.CONFIG);
        return ApiResponse.ok(Map.of("items", service.list().stream().map(FaqView::of).toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdResponse> create(@Valid @RequestBody CreateFaqRequest request) {
        return ApiResponse.ok(new IdResponse(service.create(access.require(AdminPermission.CONFIG), request.question(),
                request.answer(), request.sortNo()).getId()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateFaqRequest request) {
        service.update(access.require(AdminPermission.CONFIG), id, request.question(), request.answer(), request.sortNo());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.CONFIG), id);
        return ApiResponse.ok();
    }

    @PutMapping("/sort-order")
    public ApiResponse<Void> sort(@Valid @RequestBody SortRequest request) {
        service.sort(access.require(AdminPermission.CONFIG), request.items().stream()
                .map(e -> new FaqService.SortItem(e.id(), e.sortNo())).toList());
        return ApiResponse.ok();
    }
}
