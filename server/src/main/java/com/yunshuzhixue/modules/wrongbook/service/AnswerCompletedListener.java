package com.yunshuzhixue.modules.wrongbook.service;

import com.yunshuzhixue.modules.practice.api.AnswerCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 错题收录（需求 5.4.6、5.5）：订阅 M09 的「作答完成」事件，在发布方事务内同步处理。
 * 答错累加次数并保留上次答案；错题重做答对时移出错题本。顺序练习答对不移除。
 */
@Component
public class AnswerCompletedListener {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AnswerCompletedListener(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @EventListener
    public void on(AnswerCompletedEvent event) {
        if (event.correct()) {
            if ("wrong_redo".equals(event.practiceMode())) {
                jdbc.update("DELETE FROM wrong_question WHERE student_id = ? AND practice_question_id = ?",
                        event.studentId(), event.practiceQuestionId());
            }
            return;
        }
        jdbc.update("INSERT INTO wrong_question (student_id, practice_question_id, course_id, question_type,"
                        + " wrong_count, last_student_answer, last_wrong_at) VALUES (?, ?, ?, ?, 1, ?, NOW(3))"
                        + " ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1,"
                        + " last_student_answer = VALUES(last_student_answer), last_wrong_at = VALUES(last_wrong_at)",
                event.studentId(), event.practiceQuestionId(), event.courseId(), event.questionType(),
                json.writeValueAsString(event.studentAnswer()));
    }
}
