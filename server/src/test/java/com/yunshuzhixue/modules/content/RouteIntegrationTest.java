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
import com.yunshuzhixue.modules.content.entity.LearningRoute;
import com.yunshuzhixue.modules.content.entity.RouteStage;
import com.yunshuzhixue.modules.content.entity.StageCourse;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.LearningRouteMapper;
import com.yunshuzhixue.modules.content.mapper.RouteStageMapper;
import com.yunshuzhixue.modules.content.mapper.StageCourseMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.util.ArrayList;
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

/** 学习路线（A-CNT-14~28）。连接本地 MySQL 和 Redis，测试数据在结束后删除 */
@SpringBootTest
@AutoConfigureMockMvc
class RouteIntegrationTest {

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
    private LearningRouteMapper routeMapper;
    @Autowired
    private RouteStageMapper stageMapper;
    @Autowired
    private StageCourseMapper stageCourseMapper;
    @Autowired
    private VideoPlatformMapper platformMapper;

    private AdminTestAccounts accounts;
    private SysUser operator;
    private String token;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> courseIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        operator = accounts.create("content_operator");
        token = accounts.login(operator);
        Subject subject = new Subject();
        subject.setName("学科" + tag);
        subject.setSortNo(1);
        subjectMapper.insert(subject);
        Long platformId = platformMapper.selectList(null).stream().findFirst().map(VideoPlatform::getId).orElseThrow();
        for (int i = 1; i <= 3; i++) {
            Course course = new Course();
            course.setTitle("课程" + i + tag);
            course.setVideoPlatformId(platformId);
            course.setVideoUrl("https://www.bilibili.com/video/BV" + i);
            course.setSortNo(i);
            course.setStatus(i == 3 ? "off_shelf" : "on_shelf");
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
        for (LearningRoute route : routeMapper.selectList(new LambdaQueryWrapper<LearningRoute>().like(LearningRoute::getName, tag))) {
            stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getLearningRouteId, route.getId()));
            stageMapper.delete(new LambdaQueryWrapper<RouteStage>().eq(RouteStage::getLearningRouteId, route.getId()));
            routeMapper.deleteById(route.getId());
        }
        for (Long id : courseIds) {
            stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getCourseId, id));
            courseSubjectMapper.delete(new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getCourseId, id));
            courseMapper.deleteById(id);
        }
        subjectMapper.delete(new LambdaQueryWrapper<Subject>().like(Subject::getName, tag));
        accounts.cleanUp();
    }

    @Test
    void routeIsArrangedThenPublished() throws Exception {
        Long routeId = idOf(mvc.perform(post("/api/v1/admin/routes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"路线" + tag + "\",\"intro\":\"简介\",\"sort_no\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("off_shelf"))
                .andExpect(jsonPath("$.data.stage_count").value(0))
                .andReturn());

        // 没有阶段、没有已上架课程时不能上架
        mvc.perform(put("/api/v1/admin/routes/" + routeId + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"on_shelf\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_PUBLISH_INVALID"))
                .andExpect(jsonPath("$.errors.length()").value(2));

        Long first = stageId(routeId, "基础", 1);
        Long second = stageId(routeId, "进阶", 2);

        // 只引用已下架课程时仍不能上架
        mvc.perform(post("/api/v1/admin/stages/" + first + "/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"course_ids\":[" + courseIds.get(2) + "]}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/admin/routes/" + routeId + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"on_shelf\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(1));

        mvc.perform(post("/api/v1/admin/stages/" + first + "/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_ids\":[" + courseIds.get(0) + "," + courseIds.get(2) + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[1].course_id").value(courseIds.get(0)))
                .andExpect(jsonPath("$.data.courses[1].subject_names[0]").value("学科" + tag));

        // 同一路线内已在其他阶段的课程不能再引用
        mvc.perform(post("/api/v1/admin/stages/" + second + "/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"course_ids\":[" + courseIds.get(0) + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_ALREADY_IN_ROUTE"));
        mvc.perform(post("/api/v1/admin/stages/" + second + "/courses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"course_ids\":[" + courseIds.get(1) + "]}"))
                .andExpect(status().isCreated());

        // 阶段内课程排序、阶段排序
        mvc.perform(put("/api/v1/admin/stages/" + first + "/courses/sort-order").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + courseIds.get(0) + ",\"sort_no\":1},{\"id\":" + courseIds.get(2) + ",\"sort_no\":2}]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/routes/" + routeId + "/stages/sort-order").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + first + ",\"sort_no\":2},{\"id\":" + second + ",\"sort_no\":1}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/routes/" + routeId).header("Authorization", token))
                .andExpect(jsonPath("$.data.route.stage_count").value(2))
                .andExpect(jsonPath("$.data.route.course_count").value(3))
                .andExpect(jsonPath("$.data.stages[0].name").value("进阶"))
                .andExpect(jsonPath("$.data.stages[1].courses[0].course_id").value(courseIds.get(0)))
                .andExpect(jsonPath("$.data.stages[1].courses[1].status").value("off_shelf"));

        mvc.perform(put("/api/v1/admin/routes/" + routeId + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"on_shelf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("on_shelf"));

        // 预览只显示已上架课程，结构与学员端一致
        mvc.perform(get("/api/v1/admin/routes/" + routeId + "/preview").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stages[1].courses.length()").value(1))
                .andExpect(jsonPath("$.data.stages[1].courses[0].is_learned").value(false))
                .andExpect(jsonPath("$.data.stages[1].is_random_available").value(false));

        // 被路线引用的课程不能删除
        mvc.perform(delete("/api/v1/admin/courses/" + courseIds.get(1)).header("Authorization", token))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/v1/admin/stages/" + second + "/courses/" + courseIds.get(1)).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/stages/" + second + "/courses/" + courseIds.get(1)).header("Authorization", token))
                .andExpect(status().isNotFound());

        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "学习路线")))
                .extracting(OperationLog::getAction)
                .contains("新增路线", "新增阶段", "阶段引用课程", "调整课程排序", "调整阶段排序", "上架路线", "移出课程");
    }

    @Test
    void stageAndRouteCanBeEditedAndDeleted() throws Exception {
        Long routeId = idOf(mvc.perform(post("/api/v1/admin/routes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"路线" + tag + "\",\"sort_no\":1}")).andReturn());
        Long stage = stageId(routeId, "阶段", 1);
        mvc.perform(patch("/api/v1/admin/stages/" + stage).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"新阶段\",\"intro\":\"说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新阶段"))
                .andExpect(jsonPath("$.data.intro").value("说明"));
        mvc.perform(patch("/api/v1/admin/routes/" + routeId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"改名路线" + tag + "\",\"intro\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名路线" + tag))
                .andExpect(jsonPath("$.data.intro").doesNotExist());
        mvc.perform(post("/api/v1/admin/stages/" + stage + "/courses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"course_ids\":[" + courseIds.get(0) + "]}"));

        mvc.perform(delete("/api/v1/admin/stages/" + stage).header("Authorization", token)).andExpect(status().isOk());
        assertThat(stageCourseMapper.selectCount(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getRouteStageId, stage))).isZero();

        stageId(routeId, "再建阶段", 1);
        mvc.perform(delete("/api/v1/admin/routes/" + routeId).header("Authorization", token)).andExpect(status().isOk());
        assertThat(routeMapper.selectById(routeId)).isNull();
        assertThat(stageMapper.selectCount(new LambdaQueryWrapper<RouteStage>().eq(RouteStage::getLearningRouteId, routeId))).isZero();
        mvc.perform(get("/api/v1/admin/routes/" + routeId).header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void studentOperatorCanOnlyReadRouteOptions() throws Exception {
        idOf(mvc.perform(post("/api/v1/admin/routes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"路线" + tag + "\",\"sort_no\":1}")).andReturn());
        String studentOps = accounts.login(accounts.create("student_operator"));
        mvc.perform(get("/api/v1/admin/routes").header("Authorization", studentOps)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/route-options").header("Authorization", studentOps))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name=='路线" + tag + "')].status").value("off_shelf"));
    }

    private Long stageId(Long routeId, String name, int sortNo) throws Exception {
        return idOf(mvc.perform(post("/api/v1/admin/routes/" + routeId + "/stages").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"sort_no\":" + sortNo + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private static Long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
