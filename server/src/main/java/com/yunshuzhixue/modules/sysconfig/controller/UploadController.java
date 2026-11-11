package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.UploadResponse;
import com.yunshuzhixue.modules.sysconfig.service.ImageUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 上传客服二维码（A-CFG-18），返回的地址写入 help.contact 配置 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
public class UploadController {

    private final ImageUploadService service;
    private final AdminAccess access;

    public UploadController(ImageUploadService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        access.require(AdminPermission.CONFIG);
        return ApiResponse.ok(new UploadResponse(service.upload(file)));
    }
}
