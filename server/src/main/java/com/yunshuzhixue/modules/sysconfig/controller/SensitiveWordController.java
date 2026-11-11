package com.yunshuzhixue.modules.sysconfig.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.AddSensitiveWordRequest;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.IdResponse;
import com.yunshuzhixue.modules.sysconfig.controller.dto.ConfigDtos.SensitiveWordView;
import com.yunshuzhixue.modules.sysconfig.entity.SensitiveWord;
import com.yunshuzhixue.modules.sysconfig.service.SensitiveWordService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 敏感词库（A-CFG-05~08），仅超级管理员 */
@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
public class SensitiveWordController {

    private final SensitiveWordService service;
    private final AdminAccess access;

    public SensitiveWordController(SensitiveWordService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<PageResult<SensitiveWordView>> list(@RequestParam(required = false) Integer page,
                                                           @RequestParam(name = "page_size", required = false) Integer pageSize,
                                                           @RequestParam(required = false) String keyword) {
        access.require(AdminPermission.CONFIG);
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "关键词最多 100 个字符");
        }
        PageResult<SensitiveWord> result = service.list(PageQuery.of(page, pageSize), keyword);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(SensitiveWordView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdResponse> add(@Valid @RequestBody AddSensitiveWordRequest request) {
        return ApiResponse.ok(new IdResponse(service.add(access.require(AdminPermission.CONFIG), request.word()).getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(access.require(AdminPermission.CONFIG), id);
        return ApiResponse.ok();
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SensitiveWordService.ImportResult> importWords(@RequestParam("file") MultipartFile file) {
        var operator = access.require(AdminPermission.CONFIG);
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请上传 xlsx 格式的导入模板");
        }
        try {
            return ApiResponse.ok(service.importWords(operator, file.getInputStream()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件");
        }
    }
}
