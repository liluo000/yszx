package com.yunshuzhixue.modules.studentops.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.studentops.mapper.StudentRow;
import com.yunshuzhixue.modules.studentops.service.StudentAdminService.Criteria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** 学员管理的请求与响应（A-STU-01~06），字段与 04.API接口设计.md StudentAdminItem 一致 */
public final class StudentDtos {

    private StudentDtos() {
    }

    public static final String EDUCATION = "^(high_school_or_below|junior_college|bachelor|master_or_above)$";
    public static final String WORK_YEARS = "^(student_or_graduate|under_1_year|1_to_3_years|3_to_5_years|over_5_years)$";
    public static final String STATUS = "^(normal|banned|cancelling)$";

    public record StudentAdminItem(Long id, String nickname, String phoneMasked, Instant createdAt, Instant lastActiveAt,
                                   String education, String jobIntentName, String workYears, long learnedRouteCount,
                                   long interviewCount, String status, String banReason) {
        public static StudentAdminItem of(StudentRow r) {
            String phone = r.getPhone();
            String masked = phone != null && phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : phone;
            return new StudentAdminItem(r.getId(), r.getNickname(), masked, UtcTime.toInstant(r.getCreatedAt()),
                    UtcTime.toInstant(r.getLastActiveAt()), r.getEducation(), r.getJobIntentName(), r.getWorkYears(),
                    r.getLearnedRouteCount() == null ? 0 : r.getLearnedRouteCount(),
                    r.getInterviewCount() == null ? 0 : r.getInterviewCount(), r.getStatus(), r.getBanReason());
        }
    }

    /** 导出的筛选条件与列表一致 */
    public record ExportRequest(
            LocalDate registeredStartDate, LocalDate registeredEndDate, LocalDate activeStartDate, LocalDate activeEndDate,
            Long jobIntentLearningRouteId,
            @Pattern(regexp = EDUCATION, message = "学历不正确") String education,
            @Pattern(regexp = WORK_YEARS, message = "工作年限不正确") String workYears,
            Long learnedRouteId, Boolean hasInterview,
            @Pattern(regexp = STATUS, message = "状态不正确") String status,
            @Size(max = 30, message = "关键词最多 30 个字符") String keyword) {
        public Criteria toCriteria() {
            return new Criteria(registeredStartDate, registeredEndDate, activeStartDate, activeEndDate,
                    jobIntentLearningRouteId, education, workYears, learnedRouteId, hasInterview, status, keyword);
        }
    }

    public record BanRequest(
            @NotBlank(message = "请填写封禁原因") @Size(max = 200, message = "封禁原因最多 200 个字符") String banReason) {
    }

    public record StatusResponse(String status) {
    }

    public record PhoneResponse(String phone) {
    }
}
