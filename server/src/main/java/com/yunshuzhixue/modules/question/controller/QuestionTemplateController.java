package com.yunshuzhixue.modules.question.controller;

import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.question.service.QuestionImportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 题目导入模板（A-CNT-13 的 practice_question、interview_question），比通用的模板路径优先匹配 */
@RestController
public class QuestionTemplateController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdminAccess access;

    public QuestionTemplateController(AdminAccess access) {
        this.access = access;
    }

    @GetMapping("/api/v1/admin/import-templates/practice_question")
    public ResponseEntity<byte[]> practice() {
        access.require(AdminPermission.PRACTICE_QUESTION);
        return file("刷题题目导入模板.xlsx", ExcelSheets.template("刷题题目", QuestionImportService.PRACTICE_HEADERS));
    }

    @GetMapping("/api/v1/admin/import-templates/interview_question")
    public ResponseEntity<byte[]> interview() {
        access.require(AdminPermission.INTERVIEW_QUESTION);
        return file("面试题目导入模板.xlsx", ExcelSheets.template("面试题目", QuestionImportService.INTERVIEW_HEADERS));
    }

    private static ResponseEntity<byte[]> file(String filename, byte[] body) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
