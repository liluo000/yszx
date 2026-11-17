package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseDirectoryService implements CourseDirectory {

    private final CourseMapper courseMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final JdbcTemplate jdbc;

    public CourseDirectoryService(CourseMapper courseMapper, CourseSubjectMapper courseSubjectMapper,
                                  SubjectMapper subjectMapper, JdbcTemplate jdbc) {
        this.courseMapper = courseMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.subjectMapper = subjectMapper;
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, CourseRef> find(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseMapper.selectByIds(courseIds).stream().map(CourseDirectoryService::ref)
                .collect(Collectors.toMap(CourseRef::id, Function.identity()));
    }

    @Override
    public CourseVideo video(Long courseId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id, c.video_url, c.status,"
                + " p.name AS platform_name FROM course c"
                + " LEFT JOIN video_platform p ON p.id = c.video_platform_id WHERE c.id = ?", courseId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new CourseVideo(((Number) row.get("id")).longValue(), (String) row.get("video_url"),
                (String) row.get("platform_name"), (String) row.get("status"));
    }

    @Override
    public Map<Long, List<SubjectRef>> subjectsOf(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        List<CourseSubject> links = courseSubjectMapper.selectList(new LambdaQueryWrapper<CourseSubject>()
                .in(CourseSubject::getCourseId, courseIds)
                .orderByAsc(CourseSubject::getCourseId, CourseSubject::getSortNo));
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> subjectIds = links.stream().map(CourseSubject::getSubjectId).collect(Collectors.toSet());
        Map<Long, String> names = subjectMapper.selectByIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        Map<Long, List<SubjectRef>> result = new LinkedHashMap<>();
        for (CourseSubject link : links) {
            String name = names.get(link.getSubjectId());
            if (name == null) {
                continue;
            }
            result.computeIfAbsent(link.getCourseId(), key -> new ArrayList<>())
                    .add(new SubjectRef(link.getSubjectId(), name));
        }
        return result;
    }

    @Override
    public Set<Long> courseIdsOfSubject(Long subjectId) {
        return courseSubjectMapper.selectList(new LambdaQueryWrapper<CourseSubject>()
                        .eq(CourseSubject::getSubjectId, subjectId))
                .stream().map(CourseSubject::getCourseId).collect(Collectors.toSet());
    }

    @Override
    public List<CourseRef> all() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>().orderByAsc(Course::getId))
                .stream().map(CourseDirectoryService::ref).toList();
    }

    private static CourseRef ref(Course c) {
        return new CourseRef(c.getId(), c.getTitle(), c.getStatus());
    }
}
