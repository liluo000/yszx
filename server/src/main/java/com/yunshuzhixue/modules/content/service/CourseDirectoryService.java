package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CourseDirectoryService implements CourseDirectory {

    private final CourseMapper courseMapper;
    private final CourseSubjectMapper courseSubjectMapper;

    public CourseDirectoryService(CourseMapper courseMapper, CourseSubjectMapper courseSubjectMapper) {
        this.courseMapper = courseMapper;
        this.courseSubjectMapper = courseSubjectMapper;
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
