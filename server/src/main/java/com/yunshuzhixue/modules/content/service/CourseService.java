package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.content.api.CourseUsageProvider;
import com.yunshuzhixue.modules.content.domain.VideoUrls;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.entity.StageCourse;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.StageCourseMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.api.VideoPlatformCatalog;
import com.yunshuzhixue.modules.sysconfig.api.VideoPlatformCatalog.PlatformInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 课程库（需求 6.2、8.7，A-CNT-06~11） */
@Service
public class CourseService {

    public static final String ON_SHELF = "on_shelf";
    public static final String OFF_SHELF = "off_shelf";

    public record SubjectRef(Long id, String name) {
    }

    /** 课程及列表需要展示的名称和统计；subjects 按课程内的学科顺序排列 */
    public record CourseItem(Course course, List<SubjectRef> subjects, String platformName, long practiceQuestionCount,
                             long interviewQuestionCount, long routeReferenceCount) {
    }

    /** 新增、编辑的字段；编辑时为空的字段不修改，intro 传空字符串表示清空，subjectIds 传入时整体替换 */
    public record CourseFields(List<Long> subjectIds, String title, Long videoPlatformId, String videoUrl, String intro,
                               Integer durationMinutes, Integer sortNo) {
    }

    private final CourseMapper courseMapper;
    private final SubjectMapper subjectMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final StageCourseMapper stageCourseMapper;
    private final VideoPlatformCatalog platforms;
    private final List<CourseUsageProvider> usageProviders;
    private final OperationLogRecorder recorder;

    public CourseService(CourseMapper courseMapper, SubjectMapper subjectMapper, CourseSubjectMapper courseSubjectMapper,
                         StageCourseMapper stageCourseMapper,
                         VideoPlatformCatalog platforms, List<CourseUsageProvider> usageProviders,
                         OperationLogRecorder recorder) {
        this.courseMapper = courseMapper;
        this.subjectMapper = subjectMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.stageCourseMapper = stageCourseMapper;
        this.platforms = platforms;
        this.usageProviders = usageProviders;
        this.recorder = recorder;
    }

    public PageResult<CourseItem> list(PageQuery page, Long subjectId, String status, Boolean hasQuestions,
                                       String keyword) {
        LambdaQueryWrapper<Course> query = new LambdaQueryWrapper<Course>()
                // 属于该学科的课程（课程可属于多个学科）
                .inSql(subjectId != null, Course::getId,
                        "SELECT course_id FROM course_subject WHERE subject_id = " + subjectId)
                .eq(StringUtils.hasText(status), Course::getStatus, status)
                .like(StringUtils.hasText(keyword), Course::getTitle, keyword == null ? null : keyword.trim());
        if (hasQuestions != null) {
            Set<Long> configured = new HashSet<>();
            usageProviders.forEach(p -> configured.addAll(p.coursesWithOnShelfPractice()));
            if (hasQuestions && configured.isEmpty()) {
                return new PageResult<>(List.of(), 0, page.page(), page.pageSize());
            }
            if (!configured.isEmpty()) {
                if (hasQuestions) {
                    query.in(Course::getId, configured);
                } else {
                    query.notIn(Course::getId, configured);
                }
            }
        }
        long total = courseMapper.selectCount(query);
        query.orderByAsc(Course::getSortNo).orderByAsc(Course::getId).last(page.limitClause());
        return new PageResult<>(enrich(courseMapper.selectList(query)), total, page.page(), page.pageSize());
    }

    /** 按 ID 批量取课程及名称、统计（不存在的忽略），供路线编排和预览使用 */
    public Map<Long, CourseItem> items(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return enrich(courseMapper.selectByIds(ids)).stream()
                .collect(Collectors.toMap(i -> i.course().getId(), Function.identity()));
    }

        public CourseItem get(Long id) {
        return enrich(List.of(require(id))).get(0);
    }

    @Transactional
    public CourseItem create(CurrentAdmin operator, CourseFields fields) {
        List<Long> subjectIds = requireSubjects(fields.subjectIds());
        PlatformInfo platform = requireEnabledPlatform(fields.videoPlatformId());
        String url = fields.videoUrl().trim();
        checkUrl(url, platform);
        Course course = new Course();
        course.setTitle(fields.title().trim());
        course.setVideoPlatformId(platform.id());
        course.setVideoUrl(url);
        course.setIntro(blankToNull(fields.intro()));
        course.setDurationMinutes(fields.durationMinutes());
        course.setSortNo(fields.sortNo());
        course.setStatus(ON_SHELF);
        courseMapper.insert(course);
        saveSubjects(course.getId(), subjectIds);
        log(operator, "新增课程", course.getId(), course.getTitle());
        return get(course.getId());
    }

    /** 停用的平台不能用于新建课程，已有课程不受影响：只有换平台时才要求平台已启用 */
    @Transactional
    public CourseItem update(CurrentAdmin operator, Long id, CourseFields fields) {
        Course course = require(id);
        List<Long> subjectIds = fields.subjectIds() == null ? null : requireSubjects(fields.subjectIds());
        boolean platformChanged = fields.videoPlatformId() != null
                && !fields.videoPlatformId().equals(course.getVideoPlatformId());
        PlatformInfo platform = platformChanged ? requireEnabledPlatform(fields.videoPlatformId())
                : platforms.find(course.getVideoPlatformId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String url = fields.videoUrl() == null ? course.getVideoUrl() : fields.videoUrl().trim();
        if (platformChanged || fields.videoUrl() != null) {
            checkUrl(url, platform);
        }
        String title = fields.title() == null ? course.getTitle() : fields.title().trim();
        LambdaUpdateWrapper<Course> update = new LambdaUpdateWrapper<Course>().eq(Course::getId, id)
                .set(fields.title() != null, Course::getTitle, title)
                .set(platformChanged, Course::getVideoPlatformId, platform.id())
                .set(fields.videoUrl() != null, Course::getVideoUrl, url)
                .set(fields.intro() != null, Course::getIntro, blankToNull(fields.intro()))
                .set(fields.durationMinutes() != null, Course::getDurationMinutes, fields.durationMinutes())
                .set(fields.sortNo() != null, Course::getSortNo, fields.sortNo());
        if (StringUtils.hasText(update.getSqlSet())) {
            courseMapper.update(null, update);
        }
        if (subjectIds != null) {
            courseSubjectMapper.delete(new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getCourseId, id));
            saveSubjects(id, subjectIds);
        }
        log(operator, "编辑课程", id, title);
        return get(id);
    }

    /** 下架后路线中不显示、不计进度、题目停用；这些由各模块按课程状态判断，这里只改状态 */
    @Transactional
    public CourseItem changeStatus(CurrentAdmin operator, Long id, String status) {
        Course course = require(id);
        if (!status.equals(course.getStatus())) {
            courseMapper.update(null, new LambdaUpdateWrapper<Course>().eq(Course::getId, id).set(Course::getStatus, status));
            log(operator, ON_SHELF.equals(status) ? "上架课程" : "下架课程", id, course.getTitle());
        }
        return get(id);
    }

    /** 未被路线引用、没有题目、没有学习记录时才能删除（需求 8.7），否则返回全部原因 */
    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        Course course = require(id);
        List<String> reasons = new ArrayList<>();
        long routes = routeCounts(List.of(id)).getOrDefault(id, 0L);
        if (routes > 0) {
            reasons.add("被 " + routes + " 条学习路线引用");
        }
        usageProviders.forEach(p -> reasons.addAll(p.deletionBlockers(id)));
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "课程「" + course.getTitle() + "」不能删除，只能下架",
                    reasons.stream().map(r -> (Object) new FieldViolation("course", r)).toList());
        }
        courseSubjectMapper.delete(new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getCourseId, id));
        courseMapper.deleteById(id);
        log(operator, "删除课程", id, course.getTitle());
    }

    /* ---------------- 供批量导入使用 ---------------- */

    /** 按顺序保存课程所属的学科 */
    void saveSubjects(Long courseId, List<Long> subjectIds) {
        for (int i = 0; i < subjectIds.size(); i++) {
            CourseSubject link = new CourseSubject();
            link.setCourseId(courseId);
            link.setSubjectId(subjectIds.get(i));
            link.setSortNo(i + 1);
            courseSubjectMapper.insert(link);
        }
    }

    void checkUrl(String url, PlatformInfo platform) {
        if (!VideoUrls.belongsTo(url, platform.domains())) {
            throw new BusinessException(ErrorCode.VIDEO_DOMAIN_NOT_ALLOWED,
                    "视频链接域名不属于「" + platform.name() + "」（允许：" + String.join("、", platform.domains()) + "）",
                    List.of(new FieldViolation("video_url", "视频链接域名不属于所选平台")));
        }
    }

    void log(CurrentAdmin operator, String action, Long targetId, String title) {
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.COURSE,
                action, "course", targetId, targetId == null ? title : "课程 " + title, ClientIps.current()));
    }

    /* ---------------- 内部 ---------------- */

    /** 批量补全学科名、平台名和统计，避免逐条查询 */
    private List<CourseItem> enrich(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = courses.stream().map(Course::getId).collect(Collectors.toSet());
        List<CourseSubject> links = courseSubjectMapper.selectList(new LambdaQueryWrapper<CourseSubject>()
                .in(CourseSubject::getCourseId, ids).orderByAsc(CourseSubject::getSortNo).orderByAsc(CourseSubject::getId));
        Set<Long> subjectIds = links.stream().map(CourseSubject::getSubjectId).collect(Collectors.toSet());
        Map<Long, String> subjectNames = subjectIds.isEmpty() ? Map.of() : subjectMapper.selectByIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        Map<Long, List<SubjectRef>> subjectsByCourse = new HashMap<>();
        for (CourseSubject link : links) {
            subjectsByCourse.computeIfAbsent(link.getCourseId(), k -> new ArrayList<>())
                    .add(new SubjectRef(link.getSubjectId(), subjectNames.getOrDefault(link.getSubjectId(), "")));
        }
        Map<Long, String> platformNames = platforms.all().stream()
                .collect(Collectors.toMap(PlatformInfo::id, PlatformInfo::name));
        Map<Long, Long> routes = routeCounts(ids);
        Map<Long, Long> practice = merge(ids, CourseUsageProvider::onShelfPracticeCounts);
        Map<Long, Long> interview = merge(ids, CourseUsageProvider::onShelfInterviewCounts);
        return courses.stream().map(c -> new CourseItem(c, subjectsByCourse.getOrDefault(c.getId(), List.of()),
                platformNames.getOrDefault(c.getVideoPlatformId(), ""), practice.getOrDefault(c.getId(), 0L),
                interview.getOrDefault(c.getId(), 0L), routes.getOrDefault(c.getId(), 0L))).toList();
    }

    private Map<Long, Long> merge(Collection<Long> ids,
                                  java.util.function.BiFunction<CourseUsageProvider, Collection<Long>, Map<Long, Long>> counter) {
        Map<Long, Long> result = new HashMap<>();
        usageProviders.forEach(p -> counter.apply(p, ids).forEach((k, v) -> result.merge(k, v, Long::sum)));
        return result;
    }

    /** 被引用的路线数：同一路线内课程唯一，按路线去重计数 */
    private Map<Long, Long> routeCounts(Collection<Long> courseIds) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : stageCourseMapper.selectMaps(new QueryWrapper<StageCourse>()
                .select("course_id", "COUNT(DISTINCT learning_route_id) AS route_count")
                .in("course_id", courseIds).groupBy("course_id"))) {
            result.put(((Number) row.get("course_id")).longValue(), ((Number) row.get("route_count")).longValue());
        }
        return result;
    }

    private Course require(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return course;
    }

    /** 去重并保持顺序，全部学科须存在 */
    private List<Long> requireSubjects(List<Long> subjectIds) {
        List<Long> distinct = subjectIds.stream().distinct().toList();
        if (distinct.isEmpty() || subjectMapper.selectByIds(distinct).size() != distinct.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在",
                    List.of(new FieldViolation("subject_ids", "所选学科不存在，请刷新后重试")));
        }
        return distinct;
    }

    PlatformInfo requireEnabledPlatform(Long platformId) {
        PlatformInfo platform = platforms.find(platformId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                "视频平台不存在", List.of(new FieldViolation("video_platform_id", "视频平台不存在"))));
        if (!platform.enabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "视频平台「" + platform.name() + "」已停用",
                    List.of(new FieldViolation("video_platform_id", "该视频平台已停用，不能用于新建课程")));
        }
        return platform;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 按名称匹配时忽略大小写和空白（如"B站"与"B 站"） */
    static String nameKey(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    static <T> Map<String, T> byNameKey(Collection<T> items, Function<T, String> name) {
        Map<String, T> result = new HashMap<>();
        items.forEach(i -> result.putIfAbsent(nameKey(name.apply(i)), i));
        return result;
    }
}
