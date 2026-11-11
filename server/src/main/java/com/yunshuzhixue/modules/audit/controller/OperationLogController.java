package com.yunshuzhixue.modules.audit.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.web.RequireAdminPermission;
import com.yunshuzhixue.modules.audit.controller.dto.OperationLogExportRequest;
import com.yunshuzhixue.modules.audit.controller.dto.OperationLogView;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.service.OperationLogQueryService;
import com.yunshuzhixue.modules.audit.service.OperationLogQueryService.Filter;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志（A-AUD-01、02），仅超级管理员；只读，没有修改和删除接口 */
@RestController
@RequireAdminPermission("operation_log")
public class OperationLogController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OperationLogQueryService service;

    public OperationLogController(OperationLogQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/operation-logs")
    public ApiResponse<PageResult<OperationLogView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "sys_user_id", required = false) Long sysUserId,
            @RequestParam(required = false) String module,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PageResult<OperationLog> result = service.list(PageQuery.of(page, pageSize),
                new Filter(sysUserId, module, startDate, endDate));
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(OperationLogView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @PostMapping("/api/v1/admin/operation-log-exports")
    public ResponseEntity<byte[]> export(@Valid @RequestBody OperationLogExportRequest request) {
        byte[] body = service.export(new Filter(request.sysUserId(), request.module(), request.startDate(),
                request.endDate()));
        String filename = "操作日志_" + LocalDate.now(BeijingDates.ZONE)
                .format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
