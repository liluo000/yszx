package com.yunshuzhixue.modules.studentops.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos.BanRequest;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos.ExportRequest;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos.PhoneResponse;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos.StatusResponse;
import com.yunshuzhixue.modules.studentops.controller.dto.StudentDtos.StudentAdminItem;
import com.yunshuzhixue.modules.studentops.mapper.StudentRow;
import com.yunshuzhixue.modules.studentops.service.StudentAdminService;
import com.yunshuzhixue.modules.studentops.service.StudentAdminService.Criteria;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学员管理（A-STU-01~06）：学员运营、超级管理员 */
@RestController
@RequestMapping("/api/v1/admin")
public class StudentAdminController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StudentAdminService service;
    private final AdminAccess access;

    public StudentAdminController(StudentAdminService service, AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/students")
    public ApiResponse<PageResult<StudentAdminItem>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "registered_start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredStart,
            @RequestParam(name = "registered_end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredEnd,
            @RequestParam(name = "active_start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeStart,
            @RequestParam(name = "active_end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeEnd,
            @RequestParam(name = "job_intent_learning_route_id", required = false) Long jobIntent,
            @RequestParam(required = false) String education,
            @RequestParam(name = "work_years", required = false) String workYears,
            @RequestParam(name = "learned_route_id", required = false) Long learnedRouteId,
            @RequestParam(name = "has_interview", required = false) Boolean hasInterview,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        access.require(AdminPermission.STUDENT);
        check(education, StudentDtos.EDUCATION, "学历不正确");
        check(workYears, StudentDtos.WORK_YEARS, "工作年限不正确");
        check(status, StudentDtos.STATUS, "状态不正确");
        if (keyword != null && keyword.length() > 30) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "关键词最多 30 个字符");
        }
        Criteria criteria = new Criteria(registeredStart, registeredEnd, activeStart, activeEnd, jobIntent,
                blank(education), blank(workYears), learnedRouteId, hasInterview, blank(status), keyword);
        PageResult<StudentRow> result = service.list(PageQuery.of(page, pageSize), criteria);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(StudentAdminItem::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @GetMapping("/students/{id}")
    public ApiResponse<StudentAdminItem> get(@PathVariable Long id) {
        access.require(AdminPermission.STUDENT);
        return ApiResponse.ok(StudentAdminItem.of(service.get(id)));
    }

    @PostMapping("/students/{id}/phone-reveals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PhoneResponse> revealPhone(@PathVariable Long id) {
        return ApiResponse.ok(new PhoneResponse(service.revealPhone(access.require(AdminPermission.STUDENT), id)));
    }

    @PutMapping("/students/{id}/ban")
    public ApiResponse<StatusResponse> ban(@PathVariable Long id, @Valid @RequestBody BanRequest request) {
        service.ban(access.require(AdminPermission.STUDENT), id, request.banReason());
        return ApiResponse.ok(new StatusResponse("banned"));
    }

    @DeleteMapping("/students/{id}/ban")
    public ApiResponse<StatusResponse> unban(@PathVariable Long id) {
        service.unban(access.require(AdminPermission.STUDENT), id);
        return ApiResponse.ok(new StatusResponse("normal"));
    }

    @PostMapping("/student-exports")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        byte[] body = service.export(access.require(AdminPermission.STUDENT), request.toCriteria());
        String filename = "学员名单_" + LocalDate.now(BeijingDates.ZONE).format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private static void check(String value, String pattern, String message) {
        if (value != null && !value.isEmpty() && !value.matches(pattern)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private static String blank(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
