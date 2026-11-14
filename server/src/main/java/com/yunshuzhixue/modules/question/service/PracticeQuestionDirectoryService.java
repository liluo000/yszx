package com.yunshuzhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PracticeQuestionDirectoryService implements PracticeQuestionDirectory {

    private final PracticeQuestionMapper mapper;
    private final JsonMapper json;

    public PracticeQuestionDirectoryService(PracticeQuestionMapper mapper, JsonMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Map<Long, QuestionRef> find(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByIds(questionIds).stream()
                .map((PracticeQuestion q) -> new QuestionRef(q.getId(), q.getCourseId(), q.getStem()))
                .collect(Collectors.toMap(QuestionRef::id, Function.identity()));
    }

    @Override
    public Map<Long, QuestionDetail> details(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByIds(questionIds).stream()
                .map((PracticeQuestion q) -> new QuestionDetail(q.getId(), q.getCourseId(), q.getQuestionType(),
                        q.getStem(), parse(q.getOptions()), parse(q.getCorrectAnswer()), q.getReferenceAnswer(),
                        q.getAnalysis(), q.getStatus()))
                .collect(Collectors.toMap(QuestionDetail::id, Function.identity()));
    }

    @Override
    public List<Long> questionIdsOfCourse(Long courseId) {
        return mapper.selectList(new LambdaQueryWrapper<PracticeQuestion>()
                        .eq(PracticeQuestion::getCourseId, courseId)
                        .eq(PracticeQuestion::getStatus, "on_shelf")
                        .orderByAsc(PracticeQuestion::getSortNo, PracticeQuestion::getId))
                .stream().map(PracticeQuestion::getId).toList();
    }

    /** 库中为 JSON 文本，解析成树结构后直接回给前端；内容异常时退回空 */
    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
