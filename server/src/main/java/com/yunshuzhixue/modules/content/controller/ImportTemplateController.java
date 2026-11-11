package com.yunshuzhixue.modules.content.controller;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.content.service.CourseImportService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下载导入模板（A-CNT-13）：课程和敏感词模板（敏感词仅超级管理员）。题目模板由题库模块的 QuestionTemplateController 提供。
 */
@RestController
@RequestMapping("/api/v1/admin/import-templates")
public class ImportTemplateController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdminAccess access;

    public ImportTemplateController(AdminAccess access) {
        this.access = access;
    }

    @GetMapping("/{templateType}")
    public ResponseEntity<byte[]> download(@PathVariable String templateType) {
        return switch (templateType) {
            case "sensitive_word" -> {
                access.require(AdminPermission.CONFIG);
                yield file("敏感词导入模板.xlsx", ExcelSheets.template("敏感词", List.of("敏感词")));
            }
            case "course" -> {
                access.require(AdminPermission.COURSE);
                yield file("课程导入模板.xlsx", ExcelSheets.template("课程", CourseImportService.HEADERS));
            }
            default -> throw new BusinessException(ErrorCode.NOT_FOUND);
        };
    }

    private static ResponseEntity<byte[]> file(String filename, byte[] body) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
