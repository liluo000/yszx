package com.yunshuzhixue.modules.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshuzhixue.modules.feedback.entity.Feedback;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 反馈列表按"组"分页（需求 6.6）：待处理的反馈按同一道题（题目纠错）或同一门课（视频失效）合并为一组，
 * 已处理的每条单独一组。组键：待处理为"类型:题目或课程 ID"，已处理为"h:反馈 ID"。
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    String GROUP_KEY = "CASE WHEN status = 'pending' THEN CONCAT(feedback_type, ':', COALESCE(practice_question_id, course_id))"
            + " ELSE CONCAT('h:', id) END";

    String CONDITIONS = ""
            + "<if test='f.feedbackType != null'> AND feedback_type = #{f.feedbackType}</if>"
            + "<if test='f.status != null'> AND status = #{f.status}</if>"
            + "<if test='f.courseId != null'> AND course_id = #{f.courseId}</if>"
            + "<if test='f.errorType != null'> AND error_type = #{f.errorType}</if>"
            + "<if test='f.start != null'> AND created_at &gt;= #{f.start}</if>"
            + "<if test='f.end != null'> AND created_at &lt; #{f.end}</if>";

    String WHERE = "<where>" + CONDITIONS + "</where>";

    String WHERE_IN_KEYS = "<where>" + CONDITIONS + " AND " + GROUP_KEY
            + " IN <foreach collection='keys' item='k' open='(' separator=',' close=')'>#{k}</foreach></where>";

    /** 筛选条件；时间为 UTC */
    record Filter(String feedbackType, String status, Long courseId, String errorType, LocalDateTime start,
                  LocalDateTime end) {
    }

    @Select("<script>SELECT COUNT(DISTINCT " + GROUP_KEY + ") FROM feedback " + WHERE + "</script>")
    long countGroups(@Param("f") Filter filter);

    /** 一页的组键，按组内最新提交时间倒序 */
    @Select("<script>SELECT " + GROUP_KEY + " AS group_key FROM feedback " + WHERE
            + " GROUP BY group_key ORDER BY MAX(created_at) DESC, group_key LIMIT #{limit} OFFSET #{offset}</script>")
    List<String> pageGroupKeys(@Param("f") Filter filter, @Param("limit") int limit, @Param("offset") int offset);

    /** 这些组中符合筛选条件的全部反馈 */
    @Select("<script>SELECT * FROM feedback " + WHERE_IN_KEYS + " ORDER BY created_at DESC, id DESC</script>")
    List<Feedback> selectByGroupKeys(@Param("f") Filter filter, @Param("keys") Collection<String> keys);
}
