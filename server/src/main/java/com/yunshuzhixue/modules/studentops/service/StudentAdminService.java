package com.yunshuzhixue.modules.studentops.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.account.api.StudentAccountAdmin;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.audit.api.StudentExportLogRecorder;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.studentops.mapper.StudentQueryMapper;
import com.yunshuzhixue.modules.studentops.mapper.StudentRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 学员管理（需求 6.7，A-STU-01~06）。学习、刷题、面试记录在学员端开发时补充 */
@Service
public class StudentAdminService {

    public static final int MAX_EXPORT_ROWS = 5000;
    public static final Map<String, String> EDUCATION = Map.of("high_school_or_below", "高中及以下",
            "junior_college", "大专", "bachelor", "本科", "master_or_above", "硕士及以上");
    public static final Map<String, String> WORK_YEARS = Map.of("student_or_graduate", "在校生 / 应届生",
            "under_1_year", "1 年以内", "1_to_3_years", "1~3 年", "3_to_5_years", "3~5 年", "over_5_years", "5 年以上");
    public static final Map<String, String> STATUS = Map.of("normal", "正常", "banned", "封禁", "cancelling", "注销中");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 筛选条件，日期为北京时间 */
    public record Criteria(LocalDate registeredStartDate, LocalDate registeredEndDate, LocalDate activeStartDate,
                           LocalDate activeEndDate, Long jobIntentRouteId, String education, String workYears,
                           Long learnedRouteId, Boolean hasInterview, String status, String keyword) {
    }

    private final StudentQueryMapper queryMapper;
    private final StudentAccountAdmin accounts;
    private final OperationLogRecorder recorder;
    private final StudentExportLogRecorder exportLog;
    private final JsonMapper json;

    public StudentAdminService(StudentQueryMapper queryMapper, StudentAccountAdmin accounts, OperationLogRecorder recorder,
                               StudentExportLogRecorder exportLog, JsonMapper json) {
        this.queryMapper = queryMapper;
        this.accounts = accounts;
        this.recorder = recorder;
        this.exportLog = exportLog;
        this.json = json;
    }

    public PageResult<StudentRow> list(PageQuery page, Criteria criteria) {
        StudentQueryMapper.Filter filter = filter(criteria, false);
        long total = queryMapper.count(filter);
        List<StudentRow> rows = total == 0 ? List.of()
                : queryMapper.page(filter, page.pageSize(), (page.page() - 1) * page.pageSize());
        return new PageResult<>(rows, total, page.page(), page.pageSize());
    }

    public StudentRow get(Long id) {
        StudentRow row = queryMapper.one(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    /** 查看完整手机号：每次都记日志，日志中不写手机号（需求 6.7.2） */
    @Transactional
    public String revealPhone(CurrentAdmin operator, Long id) {
        StudentRow row = get(id);
        String phone = accounts.phoneOf(id);
        log(operator, "查看完整手机号", id, "学员 " + row.getNickname() + "（ID " + id + "）");
        return phone;
    }

    @Transactional
    public void ban(CurrentAdmin operator, Long id, String reason) {
        String trimmed = reason.strip();
        String nickname = accounts.ban(id, trimmed);
        log(operator, "封禁学员", id, "学员 " + nickname + "（ID " + id + "），原因：" + trimmed);
    }

    @Transactional
    public void unban(CurrentAdmin operator, Long id) {
        String nickname = accounts.unban(id);
        log(operator, "解封学员", id, "学员 " + nickname + "（ID " + id + "）");
    }

    /**
     * 按筛选条件导出名单（需求 6.7.4）：含完整手机号；注销中的账号不导出；单次最多 5000 条；
     * 每次导出写入 student_export_log 并记操作日志。
     */
    @Transactional
    public byte[] export(CurrentAdmin operator, Criteria criteria) {
        StudentQueryMapper.Filter filter = filter(criteria, true);
        long total = queryMapper.count(filter);
        if (total > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.EXPORT_LIMIT_EXCEEDED,
                    "共 " + total + " 条，单次最多导出 " + MAX_EXPORT_ROWS + " 条，请缩小筛选范围");
        }
        List<StudentRow> rows = total == 0 ? List.of() : queryMapper.page(filter, MAX_EXPORT_ROWS, 0);
        List<List<String>> data = new ArrayList<>();
        for (StudentRow r : rows) {
            data.add(List.of(String.valueOf(r.getId()), r.getNickname(), r.getPhone(), time(r.getCreatedAt()),
                    time(r.getLastActiveAt()), EDUCATION.getOrDefault(nz(r.getEducation()), ""), nz(r.getJobIntentName()),
                    WORK_YEARS.getOrDefault(nz(r.getWorkYears()), ""), String.valueOf(r.getLearnedRouteCount()),
                    String.valueOf(r.getInterviewCount()), STATUS.getOrDefault(r.getStatus(), r.getStatus()),
                    nz(r.getBanReason())));
        }
        String conditions = json.writeValueAsString(describe(criteria));
        exportLog.record(operator.id(), operator.name(), conditions, rows.size());
        log(operator, "导出", null, "学员名单 " + rows.size() + " 条（筛选：" + summary(criteria) + "）");
        return ExcelSheets.write("学员名单", List.of("学员 ID", "昵称", "手机号", "注册时间（北京时间）",
                "最近活跃时间（北京时间）", "学历", "求职意向", "工作年限", "学习路线数", "面试场次", "状态", "封禁原因"), data);
    }

    /* ---------------- 内部 ---------------- */

    private static StudentQueryMapper.Filter filter(Criteria c, boolean excludeCancelling) {
        check(c.registeredStartDate(), c.registeredEndDate());
        check(c.activeStartDate(), c.activeEndDate());
        String keyword = c.keyword() == null || c.keyword().isBlank() ? null : c.keyword().strip();
        return new StudentQueryMapper.Filter(start(c.registeredStartDate()), end(c.registeredEndDate()),
                start(c.activeStartDate()), end(c.activeEndDate()), c.jobIntentRouteId(), c.education(), c.workYears(),
                c.learnedRouteId(), c.hasInterview(), c.status(), keyword, excludeCancelling);
    }

    private static void check(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束日期不能早于开始日期");
        }
    }

    private static LocalDateTime start(LocalDate date) {
        return date == null ? null : BeijingDates.startOfDayUtc(date);
    }

    private static LocalDateTime end(LocalDate date) {
        return date == null ? null : BeijingDates.endOfDayUtc(date);
    }

    private static Map<String, Object> describe(Criteria c) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "registered_start_date", c.registeredStartDate());
        put(m, "registered_end_date", c.registeredEndDate());
        put(m, "active_start_date", c.activeStartDate());
        put(m, "active_end_date", c.activeEndDate());
        put(m, "job_intent_learning_route_id", c.jobIntentRouteId());
        put(m, "education", c.education());
        put(m, "work_years", c.workYears());
        put(m, "learned_route_id", c.learnedRouteId());
        put(m, "has_interview", c.hasInterview());
        put(m, "status", c.status());
        put(m, "keyword", c.keyword() == null || c.keyword().isBlank() ? null : c.keyword().strip());
        return m;
    }

    private static void put(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value instanceof LocalDate ? value.toString() : value);
        }
    }

    private static String summary(Criteria c) {
        Map<String, Object> m = describe(c);
        if (m.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        m.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join("，", parts);
    }

    private void log(CurrentAdmin operator, String action, Long targetId, String label) {
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.STUDENT,
                action, "student", targetId, label.length() > 200 ? label.substring(0, 200) : label, ClientIps.current()));
    }

    private static String time(LocalDateTime utc) {
        return utc == null ? "" : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BeijingDates.ZONE).format(TIME);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
