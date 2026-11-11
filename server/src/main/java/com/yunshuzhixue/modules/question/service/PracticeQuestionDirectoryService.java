package com.yunshuzhixue.modules.question.service;

import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PracticeQuestionDirectoryService implements PracticeQuestionDirectory {

    private final PracticeQuestionMapper mapper;

    public PracticeQuestionDirectoryService(PracticeQuestionMapper mapper) {
        this.mapper = mapper;
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
}
