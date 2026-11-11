package com.yunshuzhixue.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.feedback.entity.Feedback;
import com.yunshuzhixue.modules.feedback.mapper.FeedbackMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.message.entity.StudentMessage;
import com.yunshuzhixue.modules.message.mapper.StudentMessageMapper;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** 纠错与反馈（A-FDB-01、02）。学员端尚未开发，测试直接写入学员和反馈数据，结束后删除 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedbackIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private VideoPlatformMapper platformMapper;
    @Autowired
    private PracticeQuestionMapper questionMapper;
    @Autowired
    private FeedbackMapper feedbackMapper;
    @Autowired
    private StudentMessageMapper messageMapper;

    private AdminTestAccounts accounts;
    private SysUser operator;
    private String token;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Course course;
    private PracticeQuestion question;
    private final List<Long> studentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        operator = accounts.create("content_operator");
        token = accounts.login(operator);
        course = new Course();
        course.setTitle("课程" + tag);
        course.setVideoPlatformId(platformMapper.selectList(null).stream().findFirst().map(VideoPlatform::getId).orElseThrow());
        course.setVideoUrl("https://www.bilibili.com/video/BV1");
        course.setSortNo(1);
        course.setStatus("on_shelf");
        courseMapper.insert(course);
        question = new PracticeQuestion();
        question.setCourseId(course.getId());
        question.setQuestionType("true_false");
        question.setStem("题干" + tag);
        question.setCorrectAnswer("true");
        question.setSortNo(1);
        question.setStatus("on_shelf");
        questionMapper.insert(question);
        for (int i = 0; i < 2; i++) {
            studentIds.add(insertStudent("学员" + i + tag.substring(0, 4)));
        }
    }

    @AfterEach
    void cleanUp() {
        feedbackMapper.delete(new LambdaQueryWrapper<Feedback>().in(Feedback::getStudentId, studentIds));
        messageMapper.delete(new LambdaQueryWrapper<StudentMessage>().in(StudentMessage::getStudentId, studentIds));
        studentIds.forEach(id -> jdbc.update("DELETE FROM student WHERE id = ?", id));
        questionMapper.deleteById(question.getId());
        courseMapper.deleteById(course.getId());
        accounts.cleanUp();
    }

    @Test
    void pendingFeedbackIsMergedAndHandledTogether() throws Exception {
        Long a1 = correction(studentIds.get(0), "answer_error", "答案应为错", 3);
        Long a2 = correction(studentIds.get(0), "answer_error", "再次反馈", 2);
        Long b1 = correction(studentIds.get(1), "stem_error", "题干有错别字", 1);
        Long video = videoInvalid(studentIds.get(1), 0);

        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token)
                        .param("status", "pending").param("course_id", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].feedback_type").value("video_invalid"))
                .andExpect(jsonPath("$.data.items[0].course_title").value("课程" + tag))
                .andExpect(jsonPath("$.data.items[1].submit_count").value(3))
                .andExpect(jsonPath("$.data.items[1].feedback_ids.length()").value(3))
                .andExpect(jsonPath("$.data.items[1].stem").value("题干" + tag))
                .andExpect(jsonPath("$.data.items[1].description").value("题干有错别字"))
                .andExpect(jsonPath("$.data.items[1].student_nickname").value("学员1" + tag.substring(0, 4)));

        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token)
                        .param("course_id", course.getId().toString()).param("error_type", "answer_error"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].submit_count").value(2));

        mvc.perform(post("/api/v1/admin/feedback-handlings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback_ids\":[" + a1 + "," + a2 + "," + b1 + "],\"status\":\"corrected\",\"handling_note\":\"已修改答案\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated_count").value(3));

        // 两位学员各收到一条站内信，同一学员对同一题多次提交只发一条
        List<StudentMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<StudentMessage>().in(StudentMessage::getStudentId, studentIds));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).contains("已修正").contains("已修改答案").contains("题干" + tag);
        assertThat(messages).extracting(StudentMessage::getMessageType).containsOnly("feedback_result");

        // 已处理的反馈不再合并，逐条显示
        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token)
                        .param("status", "corrected").param("course_id", course.getId().toString()))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].handling_note").value("已修改答案"))
                .andExpect(jsonPath("$.data.items[0].submit_count").value(1));

        mvc.perform(post("/api/v1/admin/feedback-handlings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback_ids\":[" + a1 + "," + video + "],\"status\":\"rejected\",\"handling_note\":\"重复\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK_ALREADY_HANDLED"));
        assertThat(feedbackMapper.selectById(video).getStatus()).isEqualTo("pending");

        mvc.perform(post("/api/v1/admin/feedback-handlings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback_ids\":[" + video + "],\"status\":\"rejected\",\"handling_note\":\" \"}"))
                .andExpect(status().isBadRequest());

        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "纠错与反馈")))
                .extracting(OperationLog::getTargetLabel)
                .containsExactly("题目纠错：题目：题干" + tag + "，已修正（3 条，通知 2 位学员）");
    }

    @Test
    void deletedStudentAndDateFilter() throws Exception {
        Long id = videoInvalid(studentIds.get(0), 0);
        jdbc.update("DELETE FROM student WHERE id = ?", studentIds.get(0));
        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token)
                        .param("course_id", course.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].feedback_ids[0]").value(id))
                .andExpect(jsonPath("$.data.items[0].student_nickname").doesNotExist());
        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token)
                        .param("course_id", course.getId().toString()).param("start_date", "2000-01-01").param("end_date", "2000-01-02"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", token).param("status", "done"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentOperatorCannotHandleFeedback() throws Exception {
        String studentOps = accounts.login(accounts.create("student_operator"));
        mvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", studentOps)).andExpect(status().isForbidden());
    }

    private Long insertStudent(String nickname) {
        String phone = "19" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO student (phone, nickname, status) VALUES (?, ?, 'normal')",
                    new String[] {"id"});
            ps.setString(1, phone);
            ps.setString(2, nickname);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private Long correction(Long studentId, String errorType, String description, int minutesAgo) {
        Feedback f = base(studentId, "question_correction", minutesAgo);
        f.setPracticeQuestionId(question.getId());
        f.setErrorType(errorType);
        f.setDescription(description);
        feedbackMapper.insert(f);
        return f.getId();
    }

    private Long videoInvalid(Long studentId, int minutesAgo) {
        Feedback f = base(studentId, "video_invalid", minutesAgo);
        f.setVideoUrl(course.getVideoUrl());
        feedbackMapper.insert(f);
        return f.getId();
    }

    private Feedback base(Long studentId, String type, int minutesAgo) {
        Feedback f = new Feedback();
        f.setFeedbackType(type);
        f.setStudentId(studentId);
        f.setCourseId(course.getId());
        f.setStatus("pending");
        f.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(minutesAgo));
        return f;
    }
}
