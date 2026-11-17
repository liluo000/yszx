package com.yunshuzhixue.modules.feedback.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseVideo;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionRef;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学员提交反馈（S-FDB-01）：题目纠错与视频失效，服务端补齐课程与当时的视频链接 */
@Service
public class StudentFeedbackService {

    private static final List<String> ERROR_TYPES = List.of("stem_error", "answer_error", "analysis_error", "other");

    private final JdbcTemplate jdbc;
    private final PracticeQuestionDirectory questions;
    private final CourseDirectory courses;

    public StudentFeedbackService(JdbcTemplate jdbc, PracticeQuestionDirectory questions, CourseDirectory courses) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.courses = courses;
    }

    @Transactional
    public long submit(long studentId, SubmitRequest request) {
        String type = request.feedbackType();
        Long courseId;
        Long questionId = null;
        String errorType = null;
        String videoUrl = null;

        if ("video_invalid".equals(type)) {
            courseId = request.courseId();
            if (courseId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择课程");
            }
            CourseVideo video = courses.video(courseId);
            if (video == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            if (!"on_shelf".equals(video.status())) {
                throw new BusinessException(ErrorCode.CONTENT_OFF_SHELF);
            }
            videoUrl = video.videoUrl();
        } else if ("question_correction".equals(type)) {
            questionId = request.practiceQuestionId();
            if (questionId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择题目");
            }
            Map<Long, QuestionRef> found = questions.find(List.of(questionId));
            QuestionRef question = found.get(questionId);
            if (question == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            courseId = question.courseId();
            errorType = request.errorType();
            if (!ERROR_TYPES.contains(errorType)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择错误类型");
            }
            if (request.description() == null || request.description().isBlank()
                    || request.description().length() > 1000) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写 1~1000 字的说明");
            }
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "反馈类型不正确");
        }

        String description = request.description();
        if (description != null && description.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "说明不能超过 1000 字");
        }
        jdbc.update("INSERT INTO feedback (feedback_type, student_id, course_id, practice_question_id,"
                        + " error_type, description, video_url, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
                type, studentId, courseId, questionId, errorType, description, videoUrl);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public record SubmitRequest(String feedbackType, Long practiceQuestionId, String errorType,
                                String description, Long courseId) {
    }
}
