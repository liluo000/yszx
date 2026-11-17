package com.yunshuzhixue.modules.learning.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.account.api.StudentActivityRecorder;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseVideo;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习进度（M08，S-LRN-01~03）：打开课程、标记/取消已学。
 * 课程状态表归 M08；活跃记录通过 M01 的公开接口写入。
 */
@Service
public class LearningService {

    private static final String ON_SHELF = "on_shelf";

    private final JdbcTemplate jdbc;
    private final CourseDirectory courses;
    private final StudentActivityRecorder activity;

    public LearningService(JdbcTemplate jdbc, CourseDirectory courses, StudentActivityRecorder activity) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.activity = activity;
    }

    /** S-LRN-01：记录打开行为并返回视频链接；从路线进入时更新继续学习位置 */
    @Transactional
    public OpenResult open(long studentId, long courseId, Long routeId, Long stageId) {
        CourseVideo video = requireOnShelf(courseId);
        if ((routeId == null) != (stageId == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "从路线进入时路线与阶段都要传");
        }
        if (routeId != null) {
            Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM stage_course"
                    + " WHERE learning_route_id = ? AND route_stage_id = ? AND course_id = ?", Integer.class,
                    routeId, stageId, courseId);
            if (valid == null || valid == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "阶段不属于该路线或未引用该课程");
            }
        }

        jdbc.update("INSERT INTO course_open_log (student_id, course_id, learning_route_id, route_stage_id,"
                + " opened_at) VALUES (?, ?, ?, ?, NOW(3))", studentId, courseId, routeId, stageId);
        jdbc.update("INSERT INTO student_course_state (student_id, course_id, first_opened_at, last_opened_at)"
                + " VALUES (?, ?, NOW(3), NOW(3))"
                + " ON DUPLICATE KEY UPDATE last_opened_at = NOW(3)", studentId, courseId);
        if (routeId != null) {
            jdbc.update("INSERT INTO student_learning_position (student_id, learning_route_id, route_stage_id,"
                    + " course_id, last_learned_at) VALUES (?, ?, ?, ?, NOW(3))"
                    + " ON DUPLICATE KEY UPDATE learning_route_id = VALUES(learning_route_id),"
                    + " route_stage_id = VALUES(route_stage_id), course_id = VALUES(course_id),"
                    + " last_learned_at = VALUES(last_learned_at)", studentId, routeId, stageId, courseId);
        }
        activity.record(studentId);
        return new OpenResult(video.videoUrl(), video.platformName());
    }

    /** S-LRN-02：标记已学，重复调用结果相同 */
    @Transactional
    public LearnedResult markLearned(long studentId, long courseId) {
        requireOnShelf(courseId);
        jdbc.update("INSERT INTO student_course_state (student_id, course_id, is_learned, learned_at)"
                + " VALUES (?, ?, 1, NOW(3))"
                + " ON DUPLICATE KEY UPDATE is_learned = 1, learned_at = COALESCE(learned_at, NOW(3))",
                studentId, courseId);
        activity.record(studentId);
        Instant learnedAt = queryLearnedAt(studentId, courseId);
        return new LearnedResult(true, learnedAt);
    }

    /** S-LRN-03：取消已学；已掌握时不允许取消 */
    @Transactional
    public void cancelLearned(long studentId, long courseId) {
        requireOnShelf(courseId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT is_mastered FROM student_course_state"
                + " WHERE student_id = ? AND course_id = ?", studentId, courseId);
        if (!rows.isEmpty()) {
            Object value = rows.get(0).get("is_mastered");
            boolean mastered = value instanceof Boolean b ? b
                    : value instanceof Number n && n.intValue() == 1;
            if (mastered) {
                throw new BusinessException(ErrorCode.COURSE_ALREADY_MASTERED);
            }
        }
        jdbc.update("UPDATE student_course_state SET is_learned = 0, learned_at = NULL"
                + " WHERE student_id = ? AND course_id = ?", studentId, courseId);
    }

    private CourseVideo requireOnShelf(long courseId) {
        CourseVideo video = courses.video(courseId);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!ON_SHELF.equals(video.status())) {
            throw new BusinessException(ErrorCode.CONTENT_OFF_SHELF);
        }
        return video;
    }

    private Instant queryLearnedAt(long studentId, long courseId) {
        List<LocalDateTime> rows = jdbc.queryForList("SELECT learned_at FROM student_course_state"
                + " WHERE student_id = ? AND course_id = ?", LocalDateTime.class, studentId, courseId);
        return rows.isEmpty() ? null : rows.get(0).toInstant(ZoneOffset.UTC);
    }

    public record OpenResult(String videoUrl, String platformName) {
    }

    public record LearnedResult(boolean isLearned, Instant learnedAt) {
    }
}
