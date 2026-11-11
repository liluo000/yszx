package com.yunshuzhixue.modules.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 学科管理（A-CNT-01~05）。连接本地 MySQL 和 Redis，测试数据在结束后删除 */
@SpringBootTest
@AutoConfigureMockMvc
class SubjectIntegrationTest {

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

    private AdminTestAccounts accounts;
    private SysUser operator;
    private String token;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        operator = accounts.create("content_operator");
        token = accounts.login(operator);
    }

    @AfterEach
    void cleanUp() {
        courseMapper.delete(new LambdaQueryWrapper<Course>().like(Course::getTitle, tag));
        subjectMapper.delete(new LambdaQueryWrapper<Subject>().like(Subject::getName, tag));
        accounts.cleanUp();
    }

    @Test
    void contentOperatorCanCreateEditAndListSubjects() throws Exception {
        Long id = create("学科" + tag, "简介", 10);
        mvc.perform(post("/api/v1/admin/subjects").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 学科" + tag.toUpperCase() + " \",\"sort_no\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_NAME_EXISTS"));

        mvc.perform(patch("/api/v1/admin/subjects/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新学科" + tag + "\",\"intro\":\"\"}"))
                .andExpect(status().isOk());
        Subject saved = subjectMapper.selectById(id);
        assertThat(saved.getName()).isEqualTo("新学科" + tag);
        assertThat(saved.getIntro()).isNull();
        assertThat(saved.getSortNo()).isEqualTo(10);

        mvc.perform(get("/api/v1/admin/subjects").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].course_count").value(0));

        mvc.perform(post("/api/v1/admin/subjects").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "学科管理")))
                .extracting(OperationLog::getAction).containsExactly("新增学科", "编辑学科");
    }

    @Test
    void subjectWithCoursesCannotBeDeleted() throws Exception {
        Long id = create("有课学科" + tag, null, 1);
        Course course = new Course();
        course.setTitle("课程" + tag);
        course.setVideoPlatformId(platformMapper.selectList(null).stream().findFirst().map(VideoPlatform::getId).orElseThrow());
        course.setVideoUrl("https://www.bilibili.com/video/BV1xx411c7mD");
        course.setSortNo(1);
        course.setStatus("on_shelf");
        courseMapper.insert(course);
        CourseSubject link = new CourseSubject();
        link.setCourseId(course.getId());
        link.setSubjectId(id);
        link.setSortNo(1);
        courseSubjectMapper.insert(link);

        mvc.perform(get("/api/v1/admin/subjects").header("Authorization", token))
                .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].course_count").value(1));
        mvc.perform(delete("/api/v1/admin/subjects/" + id).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_IN_USE"));

        courseSubjectMapper.deleteById(link.getId());
        courseMapper.deleteById(course.getId());
        mvc.perform(delete("/api/v1/admin/subjects/" + id).header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(subjectMapper.selectById(id)).isNull();
        mvc.perform(delete("/api/v1/admin/subjects/" + id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void sortOrderIsUpdatedInBatch() throws Exception {
        Long first = create("甲" + tag, null, 10);
        Long second = create("乙" + tag, null, 20);
        mvc.perform(put("/api/v1/admin/subjects/sort-order").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + first + ",\"sort_no\":20},{\"id\":" + second + ",\"sort_no\":10}]}"))
                .andExpect(status().isOk());
        assertThat(subjectMapper.selectById(first).getSortNo()).isEqualTo(20);
        assertThat(subjectMapper.selectById(second).getSortNo()).isEqualTo(10);

        mvc.perform(put("/api/v1/admin/subjects/sort-order").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + first + ",\"sort_no\":1},{\"id\":999999999,\"sort_no\":2}]}"))
                .andExpect(status().isNotFound());
        assertThat(subjectMapper.selectById(first).getSortNo()).isEqualTo(20);
    }

    @Test
    void studentOperatorCannotManageSubjects() throws Exception {
        String studentOps = accounts.login(accounts.create("student_operator"));
        for (var request : List.of(get("/api/v1/admin/subjects"),
                post("/api/v1/admin/subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x" + tag + "\",\"sort_no\":1}"))) {
            mvc.perform(request.header("Authorization", studentOps)).andExpect(status().isForbidden());
        }
    }

    private Long create(String name, String intro, int sortNo) throws Exception {
        String introJson = intro == null ? "" : ",\"intro\":\"" + intro + "\"";
        MvcResult result = mvc.perform(post("/api/v1/admin/subjects").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sort_no\":" + sortNo + introJson + "}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
