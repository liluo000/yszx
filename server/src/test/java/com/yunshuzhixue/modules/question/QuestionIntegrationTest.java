package com.yunshuzhixue.modules.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.question.entity.InterviewQuestion;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.InterviewQuestionMapper;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 刷题题库与面试题库（A-QST-01~14）。连接本地 MySQL 和 Redis，测试数据在结束后删除 */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionIntegrationTest {

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
    private SubjectMapper subjectMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private CourseSubjectMapper courseSubjectMapper;
    @Autowired
    private VideoPlatformMapper platformMapper;
    @Autowired
    private PracticeQuestionMapper practiceMapper;
    @Autowired
    private InterviewQuestionMapper interviewMapper;

    private AdminTestAccounts accounts;
    private String token;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Subject subject;
    private final List<Long> courseIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        token = accounts.login(accounts.create("content_operator"));
        subject = new Subject();
        subject.setName("学科" + tag);
        subject.setSortNo(1);
        subjectMapper.insert(subject);
        Long platformId = platformMapper.selectList(null).stream().findFirst().map(VideoPlatform::getId).orElseThrow();
        for (String title : List.of("课程甲" + tag, "课程乙" + tag)) {
            Course course = new Course();
            course.setTitle(title);
            course.setVideoPlatformId(platformId);
            course.setVideoUrl("https://www.bilibili.com/video/BV1");
            course.setSortNo(1);
            course.setStatus("on_shelf");
            courseMapper.insert(course);
            CourseSubject link = new CourseSubject();
            link.setCourseId(course.getId());
            link.setSubjectId(subject.getId());
            link.setSortNo(1);
            courseSubjectMapper.insert(link);
            courseIds.add(course.getId());
        }
    }

    @AfterEach
    void cleanUp() {
        practiceMapper.delete(new LambdaQueryWrapper<PracticeQuestion>().in(PracticeQuestion::getCourseId, courseIds));
        interviewMapper.delete(new LambdaQueryWrapper<InterviewQuestion>().in(InterviewQuestion::getCourseId, courseIds));
        courseSubjectMapper.delete(new LambdaQueryWrapper<CourseSubject>().in(CourseSubject::getCourseId, courseIds));
        courseMapper.deleteByIds(courseIds);
        subjectMapper.deleteById(subject.getId());
        accounts.cleanUp();
    }

    @Test
    void practiceQuestionsFollowTypeRules() throws Exception {
        Long course = courseIds.get(0);
        Long single = idOf(mvc.perform(post("/api/v1/admin/practice-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":" + course + ",\"question_type\":\"single_choice\",\"stem\":\"1+1=?\","
                                + "\"options\":[{\"key\":\"A\",\"text\":\"1\"},{\"key\":\"B\",\"text\":\"2\"}],"
                                + "\"correct_answer\":[\"B\"],\"sort_no\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("on_shelf"))
                .andExpect(jsonPath("$.data.course_title").value("课程甲" + tag))
                .andExpect(jsonPath("$.data.correct_answer[0]").value("B"))
                .andExpect(jsonPath("$.data.is_answered").value(false))
                .andReturn());

        // 多选题至少 2 个正确选项；单选题只能 1 个；正确选项须在选项范围内
        mvc.perform(post("/api/v1/admin/practice-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":" + course + ",\"question_type\":\"multiple_choice\",\"stem\":\"多选\","
                                + "\"options\":[{\"key\":\"A\",\"text\":\"1\"},{\"key\":\"B\",\"text\":\"2\"}],"
                                + "\"correct_answer\":[\"A\"],\"sort_no\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("correct_answer"));
        mvc.perform(patch("/api/v1/admin/practice-questions/" + single).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correct_answer\":[\"C\"]}"))
                .andExpect(status().isBadRequest());

        // 判断题；简答题评分要点选填
        mvc.perform(post("/api/v1/admin/practice-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":" + course + ",\"question_type\":\"true_false\",\"stem\":\"地球是圆的\","
                                + "\"correct_answer\":true,\"sort_no\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.correct_answer").value(true))
                .andExpect(jsonPath("$.data.options").doesNotExist());
        mvc.perform(post("/api/v1/admin/practice-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":" + course + ",\"question_type\":\"short_answer\",\"stem\":\"简述\","
                                + "\"reference_answer\":\"答案\",\"sort_no\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scoring_points").doesNotExist());

        // 改题型为判断题时清除选项
        mvc.perform(patch("/api/v1/admin/practice-questions/" + single).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_type\":\"true_false\",\"correct_answer\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question_type").value("true_false"))
                .andExpect(jsonPath("$.data.options").doesNotExist());

        mvc.perform(get("/api/v1/admin/practice-questions").header("Authorization", token)
                        .param("subject_id", subject.getId().toString()).param("question_type", "true_false"))
                .andExpect(jsonPath("$.data.total").value(2));

        // 课程库显示上架题目数，有题目的课程不能删除；下架后不再计入
        mvc.perform(get("/api/v1/admin/courses/" + course).header("Authorization", token))
                .andExpect(jsonPath("$.data.practice_question_count").value(3));
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", token)
                        .param("subject_id", subject.getId().toString()).param("has_questions", "true"))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(delete("/api/v1/admin/courses/" + course).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].reason").value("下面有刷题题目 3 道、面试题目 0 道"));
        mvc.perform(put("/api/v1/admin/practice-questions/" + single + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"off_shelf\"}"))
                .andExpect(jsonPath("$.data.status").value("off_shelf"));
        mvc.perform(get("/api/v1/admin/courses/" + course).header("Authorization", token))
                .andExpect(jsonPath("$.data.practice_question_count").value(2));

        mvc.perform(delete("/api/v1/admin/practice-questions/" + single).header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(practiceMapper.selectById(single)).isNull();
    }

    @Test
    void practiceImportMatchesCoursesAndParsesAnswers() throws Exception {
        byte[] xlsx = workbook(new String[] {"题干", "所属课程", "题型", "难度", "选项A", "选项B", "选项C", "正确答案", "解析", "标签"},
                new String[] {"单选题干", "课程甲" + tag, "单选", "简单", "甲", "乙", "丙", "C", "解析", "JavaSE基础"},
                new String[] {"多选题干", String.valueOf(courseIds.get(1)), "多选", "", "甲", "乙", "丙", "A,B", "", ""},
                new String[] {"判断题干", "课程甲" + tag, "判断", "", "", "", "", "错", "", ""},
                new String[] {"简答题干", "课程甲" + tag, "简答题", "", "", "", "", "参考答案写在正确答案列", "", ""},
                new String[] {"多选错误", "课程甲" + tag, "多选", "", "甲", "乙", "", "A", "", ""},
                new String[] {"课程不存在", "不存在的课程" + tag, "单选", "", "甲", "乙", "", "A", "", ""},
                new String[] {"题型不对", "课程甲" + tag, "填空", "", "", "", "", "", "", ""});
        mvc.perform(multipart("/api/v1/admin/practice-questions/imports")
                        .file(new MockMultipartFile("file", "q.xlsx", null, xlsx)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(4))
                .andExpect(jsonPath("$.data.failures[0].row_no").value(6))
                .andExpect(jsonPath("$.data.failures[0].reason").value("多选题至少需要 2 个正确选项"))
                .andExpect(jsonPath("$.data.failures[1].reason").value("课程不存在：不存在的课程" + tag))
                .andExpect(jsonPath("$.data.failures[2].reason").value("题型不正确：填空（应为判断、单选、多选、简答）"));

        PracticeQuestion shortAnswer = practiceMapper.selectOne(new LambdaQueryWrapper<PracticeQuestion>()
                .eq(PracticeQuestion::getStem, "简答题干").in(PracticeQuestion::getCourseId, courseIds));
        assertThat(shortAnswer.getReferenceAnswer()).isEqualTo("参考答案写在正确答案列");
        assertThat(shortAnswer.getSortNo()).isEqualTo(3);
        PracticeQuestion trueFalse = practiceMapper.selectOne(new LambdaQueryWrapper<PracticeQuestion>()
                .eq(PracticeQuestion::getStem, "判断题干").in(PracticeQuestion::getCourseId, courseIds));
        assertThat(trueFalse.getCorrectAnswer()).isEqualTo("false");

        mvc.perform(get("/api/v1/admin/import-templates/practice_question").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void interviewQuestionsCanBeManagedAndImported() throws Exception {
        Long id = idOf(mvc.perform(post("/api/v1/admin/interview-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":" + courseIds.get(0) + ",\"stem\":\"什么是 RAG？\",\"reference_answer\":\"检索增强生成\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.is_drawn").value(false))
                .andExpect(jsonPath("$.data.scoring_points").doesNotExist())
                .andReturn());
        mvc.perform(patch("/api/v1/admin/interview-questions/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scoring_points\":\"要点\",\"followup_direction\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoring_points").value("要点"));
        mvc.perform(post("/api/v1/admin/interview-questions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"course_id\":" + courseIds.get(0) + ",\"stem\":\"缺答案\"}"))
                .andExpect(status().isBadRequest());

        byte[] xlsx = workbook(new String[] {"所属学科", "题干", "难度", "主题", "标签", "参考答案", "所属课程"},
                new String[] {"AI", "ollama 是什么？", "中等", "大模型", "Ollama", "本地部署工具", "课程乙" + tag},
                new String[] {"AI", "", "中等", "", "", "答案", "课程乙" + tag});
        mvc.perform(multipart("/api/v1/admin/interview-questions/imports")
                        .file(new MockMultipartFile("file", "iv.xlsx", null, xlsx)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(1))
                .andExpect(jsonPath("$.data.failures[0].reason").value("必填项为空：题干"));

        mvc.perform(get("/api/v1/admin/interview-questions").header("Authorization", token)
                        .param("course_id", courseIds.get(1).toString()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].stem").value("ollama 是什么？"));
        mvc.perform(get("/api/v1/admin/courses/" + courseIds.get(1)).header("Authorization", token))
                .andExpect(jsonPath("$.data.interview_question_count").value(1));

        byte[] noCourse = workbook(new String[] {"题干", "参考答案"}, new String[] {"题", "答"});
        mvc.perform(multipart("/api/v1/admin/interview-questions/imports")
                        .file(new MockMultipartFile("file", "bad.xlsx", null, noCourse)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("第 1 行表头缺少：所属课程，请使用导入模板"));

        mvc.perform(delete("/api/v1/admin/interview-questions/" + id).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void studentOperatorCannotManageQuestions() throws Exception {
        String studentOps = accounts.login(accounts.create("student_operator"));
        mvc.perform(get("/api/v1/admin/practice-questions").header("Authorization", studentOps))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/interview-questions").header("Authorization", studentOps))
                .andExpect(status().isForbidden());
    }

    private static Long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static byte[] workbook(String[] header, String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row head = sheet.createRow(0);
            for (int j = 0; j < header.length; j++) {
                head.createCell(j).setCellValue(header[j]);
            }
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    row.createCell(j).setCellValue(rows[i][j]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
