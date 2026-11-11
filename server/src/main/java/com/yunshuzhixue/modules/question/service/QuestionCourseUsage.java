package com.yunshuzhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunshuzhixue.modules.content.api.CourseUsageProvider;
import com.yunshuzhixue.modules.question.entity.InterviewQuestion;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.InterviewQuestionMapper;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 题库向课程库提供题目数、已配题课程和删除前检查（课程有题目时不能删除，需求 8.7） */
@Component
public class QuestionCourseUsage implements CourseUsageProvider {

    private final PracticeQuestionMapper practiceMapper;
    private final InterviewQuestionMapper interviewMapper;

    public QuestionCourseUsage(PracticeQuestionMapper practiceMapper, InterviewQuestionMapper interviewMapper) {
        this.practiceMapper = practiceMapper;
        this.interviewMapper = interviewMapper;
    }

    @Override
    public Map<Long, Long> onShelfPracticeCounts(Collection<Long> courseIds) {
        return courseIds.isEmpty() ? Map.of() : counts(practiceMapper.selectMaps(new QueryWrapper<PracticeQuestion>()
                .select("course_id", "COUNT(*) AS n").in("course_id", courseIds).eq("status", "on_shelf")
                .groupBy("course_id")));
    }

    @Override
    public Map<Long, Long> onShelfInterviewCounts(Collection<Long> courseIds) {
        return courseIds.isEmpty() ? Map.of() : counts(interviewMapper.selectMaps(new QueryWrapper<InterviewQuestion>()
                .select("course_id", "COUNT(*) AS n").in("course_id", courseIds).eq("status", "on_shelf")
                .groupBy("course_id")));
    }

    @Override
    public Set<Long> coursesWithOnShelfPractice() {
        return practiceMapper.selectObjs(new QueryWrapper<PracticeQuestion>()
                        .select("DISTINCT course_id").eq("status", "on_shelf"))
                .stream().map(o -> ((Number) o).longValue()).collect(Collectors.toSet());
    }

    @Override
    public List<String> deletionBlockers(Long courseId) {
        List<String> reasons = new ArrayList<>();
        long practice = practiceMapper.selectCount(new QueryWrapper<PracticeQuestion>().eq("course_id", courseId));
        long interview = interviewMapper.selectCount(new QueryWrapper<InterviewQuestion>().eq("course_id", courseId));
        if (practice > 0 || interview > 0) {
            reasons.add("下面有刷题题目 " + practice + " 道、面试题目 " + interview + " 道");
        }
        return reasons;
    }

    private static Map<Long, Long> counts(List<Map<String, Object>> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("course_id")).longValue(), ((Number) row.get("n")).longValue());
        }
        return result;
    }
}
