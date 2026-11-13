package com.yunshuzhixue.modules.studentops.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学员多条件筛选（需求 6.7.1）。按 02.整体架构设计.md 4.3 的只读查询例外，跨模块关联学员、课程状态、路线编排和面试表，只读不写。
 * "学过某条路线"按路线当前编排判断：路线内任一门已上架课程被学员打开过、标记过已学或作答过（有课程状态记录）。
 */
@Mapper
public interface StudentQueryMapper {

    /** 时间均为 UTC；keyword 匹配昵称或手机号后四位 */
    record Filter(LocalDateTime registeredStart, LocalDateTime registeredEnd, LocalDateTime activeStart,
                  LocalDateTime activeEnd, Long jobIntentRouteId, String education, String workYears, Long learnedRouteId,
                  Boolean hasInterview, String status, String keyword, boolean excludeCancelling) {
    }

    String LEARNED_ROUTES = "(SELECT COUNT(DISTINCT sc.learning_route_id) FROM stage_course sc"
            + " JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
            + " JOIN student_course_state st ON st.course_id = sc.course_id AND st.student_id = s.id)";

    String INTERVIEWS = "(SELECT COUNT(*) FROM interview_session i WHERE i.student_id = s.id)";

    String WHERE = "<where>"
            + "<if test='f.registeredStart != null'> AND s.created_at &gt;= #{f.registeredStart}</if>"
            + "<if test='f.registeredEnd != null'> AND s.created_at &lt; #{f.registeredEnd}</if>"
            + "<if test='f.activeStart != null'> AND s.last_active_at &gt;= #{f.activeStart}</if>"
            + "<if test='f.activeEnd != null'> AND s.last_active_at &lt; #{f.activeEnd}</if>"
            + "<if test='f.jobIntentRouteId != null'> AND s.job_intent_learning_route_id = #{f.jobIntentRouteId}</if>"
            + "<if test='f.education != null'> AND s.education = #{f.education}</if>"
            + "<if test='f.workYears != null'> AND s.work_years = #{f.workYears}</if>"
            + "<if test='f.status != null'> AND s.status = #{f.status}</if>"
            + "<if test='f.excludeCancelling'> AND s.status &lt;&gt; 'cancelling'</if>"
            + "<if test='f.keyword != null'> AND (s.nickname LIKE CONCAT('%', #{f.keyword}, '%') OR s.phone_last4 = #{f.keyword})</if>"
            + "<if test='f.learnedRouteId != null'> AND EXISTS (SELECT 1 FROM stage_course sc"
            + " JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
            + " JOIN student_course_state st ON st.course_id = sc.course_id AND st.student_id = s.id"
            + " WHERE sc.learning_route_id = #{f.learnedRouteId})</if>"
            + "<if test='f.hasInterview != null and f.hasInterview'> AND EXISTS (SELECT 1 FROM interview_session i WHERE i.student_id = s.id)</if>"
            + "<if test='f.hasInterview != null and !f.hasInterview'> AND NOT EXISTS (SELECT 1 FROM interview_session i WHERE i.student_id = s.id)</if>"
            + "</where>";

    String COLUMNS = "s.id, s.nickname, s.phone, s.created_at, s.last_active_at, s.education, s.job_intent_name,"
            + " s.work_years, s.status, s.ban_reason, " + LEARNED_ROUTES + " AS learned_route_count, "
            + INTERVIEWS + " AS interview_count";

    @Select("<script>SELECT COUNT(*) FROM student s " + WHERE + "</script>")
    long count(@Param("f") Filter filter);

    @Select("<script>SELECT " + COLUMNS + " FROM student s " + WHERE
            + " ORDER BY s.created_at DESC, s.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<StudentRow> page(@Param("f") Filter filter, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + COLUMNS + " FROM student s WHERE s.id = #{id}")
    StudentRow one(@Param("id") Long id);
}
