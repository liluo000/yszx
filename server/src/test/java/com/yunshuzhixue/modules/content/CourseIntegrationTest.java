package com.yunshuzhixue.modules.content;

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
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.entity.StageCourse;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.StageCourseMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatformDomain;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformDomainMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.io.ByteArrayOutputStream;
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

/** 课程库（A-CNT-06~13）。连接本地 MySQL 和 Redis，测试数据在结束后删除 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseIntegrationTest {

    private static final String BILI_URL = "https://www.bilibili.com/video/BV1xx411c7mD";

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
    private StageCourseMapper stageCourseMapper;
    @Autowired
    private CourseSubjectMapper courseSubjectMapper;
    @Autowired
    private VideoPlatformMapper platformMapper;
    @Autowired
    private VideoPlatformDomainMapper domainMapper;

    private AdminTestAccounts accounts;
    private SysUser operator;
    private String token;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Subject subject;
    private Subject second;
    private Long biliId;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        operator = accounts.create("content_operator");
        token = accounts.login(operator);
        subject = new Subject();
        subject.setName("学科" + tag);
        subject.setSortNo(1);
        subjectMapper.insert(subject);
        second = new Subject();
        second.setName("第二学科" + tag);
        second.setSortNo(2);
        subjectMapper.insert(second);
        biliId = platformMapper.selectOne(new LambdaQueryWrapper<VideoPlatform>().eq(VideoPlatform::getName, "B 站")).getId();
    }

    @AfterEach
    void cleanUp() {
        for (Course course : courseMapper.selectList(new LambdaQueryWrapper<Course>().like(Course::getTitle, tag))) {
            stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getCourseId, course.getId()));
            courseSubjectMapper.delete(new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getCourseId, course.getId()));
            courseMapper.deleteById(course.getId());
        }
        subjectMapper.delete(new LambdaQueryWrapper<Subject>().like(Subject::getName, tag));
        for (VideoPlatform platform : platformMapper.selectList(
                new LambdaQueryWrapper<VideoPlatform>().like(VideoPlatform::getName, tag))) {
            domainMapper.delete(new LambdaQueryWrapper<VideoPlatformDomain>()
                    .eq(VideoPlatformDomain::getVideoPlatformId, platform.getId()));
            platformMapper.deleteById(platform.getId());
        }
        accounts.cleanUp();
    }

    @Test
    void createValidatesVideoDomainAndDefaultsToOnShelf() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(courseJson("课程" + tag, BILI_URL, biliId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("on_shelf"))
                .andExpect(jsonPath("$.data.subjects[0].name").value("学科" + tag))
                .andExpect(jsonPath("$.data.platform_name").value("B 站"))
                .andExpect(jsonPath("$.data.route_reference_count").value(0))
                .andExpect(jsonPath("$.data.practice_question_count").value(0))
                .andReturn();
        Long id = idOf(created);

        // 短链域名 b23.tv 也属于 B 站；其他域名、非 http 链接都不允许
        mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"video_url\":\"https://b23.tv/abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video_url").value("https://b23.tv/abc"));
        for (String url : new String[] {"https://www.youtube.com/watch?v=1", "https://evilbilibili.com/x", "ftp://bilibili.com/x"}) {
            mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"video_url\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VIDEO_DOMAIN_NOT_ALLOWED"));
        }

        mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void disabledPlatformCannotBeUsedForNewCoursesButExistingCoursesKeepWorking() throws Exception {
        VideoPlatform platform = new VideoPlatform();
        platform.setName("平台" + tag);
        platform.setStatus("enabled");
        platformMapper.insert(platform);
        VideoPlatformDomain domain = new VideoPlatformDomain();
        domain.setVideoPlatformId(platform.getId());
        domain.setDomain(tag + ".example.com");
        domainMapper.insert(domain);
        Long id = idOf(mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson("课程" + tag, "https://" + tag + ".example.com/v/1", platform.getId())))
                .andExpect(status().isCreated()).andReturn());

        platform.setStatus("disabled");
        platformMapper.updateById(platform);
        mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson("新课程" + tag, "https://" + tag + ".example.com/v/2", platform.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("video_platform_id"));
        mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名课程" + tag + "\",\"video_url\":\"https://" + tag + ".example.com/v/3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("改名课程" + tag));
    }

    @Test
    void listFiltersByStatusSubjectAndKeyword() throws Exception {
        Long first = idOf(mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(courseJson("Python 入门" + tag, BILI_URL, biliId))).andReturn());
        idOf(mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(courseJson("Java 入门" + tag, BILI_URL, biliId))).andReturn());

        mvc.perform(put("/api/v1/admin/courses/" + first + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"off_shelf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("off_shelf"));

        mvc.perform(get("/api/v1/admin/courses").header("Authorization", token)
                        .param("subject_id", subject.getId().toString()))
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", token)
                        .param("subject_id", subject.getId().toString()).param("status", "on_shelf"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Java 入门" + tag));
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", token)
                        .param("keyword", "python 入门" + tag))
                .andExpect(jsonPath("$.data.total").value(1));
        // 题库尚未开发，所有课程都按未配题处理
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", token)
                        .param("subject_id", subject.getId().toString()).param("has_questions", "false"))
                .andExpect(jsonPath("$.data.total").value(2));

        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "课程库")))
                .extracting(OperationLog::getAction).containsExactly("新增课程", "新增课程", "下架课程");
    }

    @Test
    void courseReferencedByRouteCannotBeDeleted() throws Exception {
        Long id = idOf(mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(courseJson("课程" + tag, BILI_URL, biliId))).andReturn());
        StageCourse reference = new StageCourse();
        reference.setLearningRouteId(999_000_001L);
        reference.setRouteStageId(999_000_002L);
        reference.setCourseId(id);
        reference.setSortNo(1);
        stageCourseMapper.insert(reference);

        mvc.perform(get("/api/v1/admin/courses/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.data.route_reference_count").value(1));
        mvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_IN_USE"))
                .andExpect(jsonPath("$.errors[0].reason").value("被 1 条学习路线引用"));
        mvc.perform(delete("/api/v1/admin/subjects/" + subject.getId()).header("Authorization", token))
                .andExpect(status().isConflict());

        stageCourseMapper.deleteById(reference.getId());
        mvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(courseMapper.selectById(id)).isNull();
    }

    @Test
    void importAddsValidRowsAndReportsFailures() throws Exception {
        int maxSort = courseMapper.selectList(null).stream().mapToInt(Course::getSortNo).max().orElse(0);
        Course existing = new Course();
        existing.setTitle("已有课程" + tag);
        existing.setVideoPlatformId(biliId);
        existing.setVideoUrl(BILI_URL);
        existing.setSortNo(maxSort + 5);
        existing.setStatus("on_shelf");
        courseMapper.insert(existing);

        byte[] xlsx = workbook(
                new String[] {"导入一" + tag, "学科" + tag.toUpperCase(), "b 站", BILI_URL, "简介", "90"},
                new String[] {"导入二" + tag, "学科" + tag, "B 站", "https://b23.tv/x", "", ""},
                new String[] {"", "学科" + tag, "B 站", BILI_URL, "", ""},
                new String[] {"导入四" + tag, "不存在的学科" + tag, "B 站", BILI_URL, "", ""},
                new String[] {"导入五" + tag, "学科" + tag, "B 站", "https://youtube.com/x", "", ""},
                new String[] {"导入六" + tag, "学科" + tag, "B 站", BILI_URL, "", "1.5"});
        mvc.perform(multipart("/api/v1/admin/courses/imports")
                        .file(new MockMultipartFile("file", "courses.xlsx", null, xlsx)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2))
                .andExpect(jsonPath("$.data.failure_count").value(4))
                .andExpect(jsonPath("$.data.failures[0].row_no").value(4))
                .andExpect(jsonPath("$.data.failures[0].reason").value("必填项为空：课程名称"))
                .andExpect(jsonPath("$.data.failures[1].reason").value("学科不存在：不存在的学科" + tag))
                .andExpect(jsonPath("$.data.failures[2].reason").value("视频链接域名不属于「B 站」"))
                .andExpect(jsonPath("$.data.failures[3].reason").value("时长需为 0~100000 的整数（分钟）"));

        Course first = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getTitle, "导入一" + tag));
        Course secondCourse = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getTitle, "导入二" + tag));
        assertThat(first.getSortNo()).isEqualTo(maxSort + 6);
        assertThat(first.getDurationMinutes()).isEqualTo(90);
        assertThat(first.getStatus()).isEqualTo("on_shelf");
        assertThat(secondCourse.getSortNo()).isEqualTo(maxSort + 7);

        mvc.perform(get("/api/v1/admin/import-templates/course").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void courseCanBelongToSeveralSubjects() throws Exception {
        String body = courseJson("多学科课程" + tag, BILI_URL, biliId)
                .replace("\"subject_ids\":[" + subject.getId() + "]",
                        "\"subject_ids\":[" + second.getId() + "," + subject.getId() + "," + second.getId() + "]");
        Long id = idOf(mvc.perform(post("/api/v1/admin/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjects.length()").value(2))
                .andExpect(jsonPath("$.data.subjects[0].name").value("第二学科" + tag))
                .andReturn());

        for (Subject s : new Subject[] {subject, second}) {
            mvc.perform(get("/api/v1/admin/courses").header("Authorization", token).param("subject_id", s.getId().toString()))
                    .andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/v1/admin/subjects").header("Authorization", token))
                    .andExpect(jsonPath("$.data.items[?(@.id==" + s.getId() + ")].course_count").value(1));
        }
        mvc.perform(delete("/api/v1/admin/subjects/" + second.getId()).header("Authorization", token))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject_ids\":[" + subject.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjects.length()").value(1));
        mvc.perform(delete("/api/v1/admin/subjects/" + second.getId()).header("Authorization", token))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject_ids\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject_ids\":[999999999]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importRecognizesColumnsByHeaderAndSplitsSubjects() throws Exception {
        Subject embedded = new Subject();
        embedded.setName("嵌入式" + tag);
        embedded.setSortNo(3);
        subjectMapper.insert(embedded);
        Subject robot = new Subject();
        robot.setName("机器人" + tag);
        robot.setSortNo(4);
        subjectMapper.insert(robot);

        // 与用户提供的 B 站数据文件相同的列：来源、BV号、封面、播放链接，顺序与模板不同
        byte[] xlsx = workbookWithHeader(new String[] {"课程名称", "所属学科", "来源", "时长（分钟）", "BV号", "封面", "播放链接"},
                new String[] {"甲" + tag, "学科" + tag + ",第二学科" + tag, "B站", "900", "BV1", "https://i1.hdslb.com/x.jpg", BILI_URL},
                new String[] {"乙" + tag, "嵌入式" + tag + "_机器人" + tag, "B站", "211", "BV2", "", BILI_URL},
                new String[] {"丙" + tag, "学科" + tag + "、不存在" + tag, "B站", "10", "BV3", "", BILI_URL});
        mvc.perform(multipart("/api/v1/admin/courses/imports")
                        .file(new MockMultipartFile("file", "bili.xlsx", null, xlsx)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2))
                .andExpect(jsonPath("$.data.failures[0].row_no").value(4))
                .andExpect(jsonPath("$.data.failures[0].reason").value("学科不存在：不存在" + tag));

        Course first = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getTitle, "甲" + tag));
        assertThat(first.getDurationMinutes()).isEqualTo(900);
        assertThat(first.getVideoUrl()).isEqualTo(BILI_URL);
        mvc.perform(get("/api/v1/admin/courses/" + first.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.data.subjects[0].name").value("学科" + tag))
                .andExpect(jsonPath("$.data.subjects[1].name").value("第二学科" + tag));
        Course secondCourse = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getTitle, "乙" + tag));
        mvc.perform(get("/api/v1/admin/courses/" + secondCourse.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.data.subjects[0].name").value("嵌入式" + tag))
                .andExpect(jsonPath("$.data.subjects[1].name").value("机器人" + tag));

        byte[] noUrl = workbookWithHeader(new String[] {"课程名称", "所属学科", "来源"}, new String[] {"丁" + tag, "学科" + tag, "B站"});
        mvc.perform(multipart("/api/v1/admin/courses/imports")
                        .file(new MockMultipartFile("file", "bad.xlsx", null, noUrl)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID"))
                .andExpect(jsonPath("$.message").value("第 1 行表头缺少：视频链接，请使用课程导入模板"));
    }

    @Test
    void studentOperatorCannotManageCourses() throws Exception {
        String studentOps = accounts.login(accounts.create("student_operator"));
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", studentOps)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/import-templates/course").header("Authorization", studentOps))
                .andExpect(status().isForbidden());
    }

    private String courseJson(String title, String url, Long platformId) {
        return "{\"subject_ids\":[" + subject.getId() + "],\"title\":\"" + title + "\",\"video_platform_id\":" + platformId
                + ",\"video_url\":\"" + url + "\",\"duration_minutes\":30,\"sort_no\":10}";
    }

    private static Long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static byte[] workbook(String[]... rows) throws Exception {
        return workbookWithHeader(new String[] {"课程名称（必填）", "所属学科（必填）", "视频平台（必填）", "视频链接（必填）", "简介", "时长（分钟）"}, rows);
    }

    private static byte[] workbookWithHeader(String[] header, String[]... rows) throws Exception {
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
