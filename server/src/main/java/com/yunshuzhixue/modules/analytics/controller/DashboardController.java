package com.yunshuzhixue.modules.analytics.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.infra.excel.ExcelSheets.SheetData;
import com.yunshuzhixue.modules.analytics.service.DashboardService;
import com.yunshuzhixue.modules.analytics.service.DashboardService.Dashboard;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据看板（A-DSH-01、02）：学员运营、超级管理员；只含统计数字，不含学员个人信息 */
@RestController
@RequestMapping("/api/v1/admin")
public class DashboardController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Map<String, String> GRANULARITY = Map.of("day", "按日", "week", "按周", "month", "按月");

    public record ExportRequest(@NotNull(message = "请选择开始日期") LocalDate startDate,
                                @NotNull(message = "请选择结束日期") LocalDate endDate,
                                @Pattern(regexp = "^(day|week|month)$", message = "统计粒度不正确") String granularity) {
    }

    private final DashboardService service;
    private final AdminAccess access;
    private final OperationLogRecorder recorder;

    public DashboardController(DashboardService service, AdminAccess access, OperationLogRecorder recorder) {
        this.service = service;
        this.access = access;
        this.recorder = recorder;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard(
            @RequestParam(name = "start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String granularity) {
        access.require(AdminPermission.DASHBOARD);
        return ApiResponse.ok(service.load(start, end, granularity(granularity)));
    }

    @PostMapping("/dashboard-exports")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        CurrentAdmin operator = access.require(AdminPermission.DASHBOARD);
        String granularity = granularity(request.granularity());
        Dashboard d = service.load(request.startDate(), request.endDate(), granularity);
        String period = request.startDate() + " 至 " + request.endDate() + "（" + GRANULARITY.get(granularity) + "）";
        byte[] body = ExcelSheets.writeSheets(List.of(
                new SheetData("获客与活跃", List.of("周期开始日期", "新增注册数", "累计学员数", "日活跃学员数", "月活跃学员数",
                        "次日留存率(%)", "7 日留存率(%)"),
                        java.util.stream.IntStream.range(0, d.acquisition().size()).mapToObj(i -> List.of(
                                d.acquisition().get(i).statDate().toString(), s(d.acquisition().get(i).newStudentCount()),
                                s(d.acquisition().get(i).cumulativeStudentCount()), s(d.activity().get(i).dailyActiveStudentCount()),
                                s(d.activity().get(i).monthlyActiveStudentCount()), s(d.activity().get(i).nextDayRetentionRate()),
                                s(d.activity().get(i).sevenDayRetentionRate()))).toList()),
                new SheetData("路线", List.of("路线", "学习人数", "平均已学进度(%)", "平均已掌握进度(%)"),
                        d.routes().stream().map(r -> List.of(r.routeName(), s(r.learnerCount()),
                                s(r.averageLearnedProgress()), s(r.averageMasteredProgress()))).toList()),
                new SheetData("刷题与面试", List.of("指标", "数值"), List.of(
                        List.of("刷题量", s(d.practice().answerCount())),
                        List.of("正确率(%)", s(d.practice().correctRate())),
                        List.of("面试场次", s(d.interview().interviewCount())),
                        List.of("面试平均分", s(d.interview().averageScore())))),
                new SheetData("错误率最高的题目", List.of("题目 ID", "题干", "作答次数", "答错次数", "错误率(%)"),
                        d.topWrongQuestions().stream().map(q -> List.of(s(q.practiceQuestionId()), q.stemExcerpt(),
                                s(q.answerCount()), s(q.wrongCount()), s(q.wrongRate()))).toList()),
                new SheetData("各阶段平均得分", List.of("阶段", "平均得分"),
                        d.stageScores().stream().map(st -> List.of(st.stageName(), s(st.averageScore()))).toList()),
                new SheetData("AI 调用次数", List.of("周期开始日期", "面试", "简答题评分"),
                        d.aiUsage().stream().map(a -> List.of(a.statDate().toString(), s(a.aiInterviewCallCount()),
                                s(a.aiScoringCallCount()))).toList())));
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.DASHBOARD,
                "导出", "dashboard", null, "看板数据 " + period, ClientIps.current()));
        String filename = "数据看板_" + LocalDate.now(BeijingDates.ZONE).format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private static String granularity(String value) {
        if (value == null || value.isEmpty()) {
            return "day";
        }
        if (!GRANULARITY.containsKey(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "统计粒度不正确");
        }
        return value;
    }

    private static String s(Object value) {
        if (value == null) {
            return "—";
        }
        return value instanceof BigDecimal b ? b.toPlainString() : String.valueOf(value);
    }
}
