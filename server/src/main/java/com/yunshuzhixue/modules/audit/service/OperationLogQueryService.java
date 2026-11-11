package com.yunshuzhixue.modules.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.web.AdminPrincipal;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 操作日志查询与导出（A-AUD-01、02）：日志只读，不能修改或手动删除，满 1 年自动清理 */
@Service
public class OperationLogQueryService {

    public static final int MAX_EXPORT_ROWS = 10_000;

    /** 查询条件，日期为北京时间 */
    public record Filter(Long sysUserId, String module, LocalDate startDate, LocalDate endDate) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> ROLE_LABEL = Map.of(
            "super_admin", "超级管理员", "content_operator", "内容运营", "student_operator", "学员运营");
    private static final List<String> HEADERS =
            List.of("时间（北京时间）", "操作人", "角色", "模块", "操作类型", "操作对象", "IP 地址");

    private final OperationLogMapper mapper;
    private final OperationLogRecorder recorder;

    public OperationLogQueryService(OperationLogMapper mapper, OperationLogRecorder recorder) {
        this.mapper = mapper;
        this.recorder = recorder;
    }

    public PageResult<OperationLog> list(PageQuery page, Filter filter) {
        LambdaQueryWrapper<OperationLog> query = query(filter);
        long total = mapper.selectCount(query);
        query.orderByDesc(OperationLog::getCreatedAt).orderByDesc(OperationLog::getId).last(page.limitClause());
        return new PageResult<>(mapper.selectList(query), total, page.page(), page.pageSize());
    }

    /** 按筛选条件导出全部记录（不分页），导出本身记入操作日志 */
    public byte[] export(Filter filter) {
        LambdaQueryWrapper<OperationLog> query = query(filter);
        long total = mapper.selectCount(query);
        if (total > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.EXPORT_LIMIT_EXCEEDED,
                    "共 " + total + " 条，单次最多导出 " + MAX_EXPORT_ROWS + " 条，请缩小筛选范围");
        }
        query.orderByDesc(OperationLog::getCreatedAt).orderByDesc(OperationLog::getId);
        List<List<String>> rows = new ArrayList<>();
        for (OperationLog log : mapper.selectList(query)) {
            rows.add(List.of(
                    log.getCreatedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(BeijingDates.ZONE).format(TIME),
                    log.getSysUserName(), ROLE_LABEL.getOrDefault(log.getRole(), log.getRole()), log.getModule(),
                    log.getAction(), nullToEmpty(log.getTargetLabel()), nullToEmpty(log.getIpAddress())));
        }
        AdminPrincipal operator = AdminPrincipal.current();
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(),
                AuditModules.OPERATION_LOG, "导出", "operation_log", null, describe(filter, rows.size()),
                ClientIps.current()));
        return ExcelSheets.write("操作日志", HEADERS, rows);
    }

    private LambdaQueryWrapper<OperationLog> query(Filter filter) {
        if (filter.startDate() != null && filter.endDate() != null && filter.endDate().isBefore(filter.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束日期不能早于开始日期");
        }
        return new LambdaQueryWrapper<OperationLog>()
                .eq(filter.sysUserId() != null, OperationLog::getSysUserId, filter.sysUserId())
                .eq(StringUtils.hasText(filter.module()), OperationLog::getModule, filter.module())
                .ge(filter.startDate() != null, OperationLog::getCreatedAt,
                        filter.startDate() == null ? null : BeijingDates.startOfDayUtc(filter.startDate()))
                .lt(filter.endDate() != null, OperationLog::getCreatedAt,
                        filter.endDate() == null ? null : BeijingDates.endOfDayUtc(filter.endDate()));
    }

    private static String describe(Filter filter, int count) {
        List<String> parts = new ArrayList<>();
        if (filter.sysUserId() != null) {
            parts.add("操作人 ID " + filter.sysUserId());
        }
        if (StringUtils.hasText(filter.module())) {
            parts.add("模块 " + filter.module());
        }
        if (filter.startDate() != null || filter.endDate() != null) {
            parts.add("日期 " + (filter.startDate() == null ? "不限" : filter.startDate()) + " 至 "
                    + (filter.endDate() == null ? "不限" : filter.endDate()));
        }
        return "操作日志 " + count + " 条（筛选：" + (parts.isEmpty() ? "无" : String.join("，", parts)) + "）";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
