package com.yunshuzhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.question.api.InterviewQuestionDirectory;
import com.yunshuzhixue.modules.question.entity.InterviewQuestion;
import com.yunshuzhixue.modules.question.mapper.InterviewQuestionMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InterviewQuestionDirectoryService implements InterviewQuestionDirectory {

    private final InterviewQuestionMapper mapper;

    public InterviewQuestionDirectoryService(InterviewQuestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Question> questionsOfCourses(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getCourseId, courseIds)
                        .eq(InterviewQuestion::getStatus, "on_shelf")
                        .orderByAsc(InterviewQuestion::getId))
                .stream()
                .map(q -> new Question(q.getId(), q.getCourseId(), q.getStem(), q.getReferenceAnswer(),
                        q.getScoringPoints(), q.getFollowupDirection()))
                .toList();
    }

    @Override
    public Map<Long, Long> countsOfCourses(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = questionsOfCourses(courseIds).stream()
                .collect(Collectors.groupingBy(Question::courseId, LinkedHashMap::new, Collectors.counting()));
        Map<Long, Long> result = new LinkedHashMap<>();
        courseIds.forEach(id -> result.put(id, 0L));
        result.putAll(counts);
        return result;
    }
}
