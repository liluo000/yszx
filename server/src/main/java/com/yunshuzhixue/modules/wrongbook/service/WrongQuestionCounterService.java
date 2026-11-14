package com.yunshuzhixue.modules.wrongbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionDetail;
import com.yunshuzhixue.modules.wrongbook.api.WrongQuestionCounter;
import com.yunshuzhixue.modules.wrongbook.entity.WrongQuestion;
import com.yunshuzhixue.modules.wrongbook.mapper.WrongQuestionMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WrongQuestionCounterService implements WrongQuestionCounter {

    private static final String ON_SHELF = "on_shelf";

    private final WrongQuestionMapper mapper;
    private final PracticeQuestionDirectory questions;

    public WrongQuestionCounterService(WrongQuestionMapper mapper, PracticeQuestionDirectory questions) {
        this.mapper = mapper;
        this.questions = questions;
    }

    @Override
    public long countRedoable(long studentId) {
        List<WrongQuestion> rows = mapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .select(WrongQuestion::getPracticeQuestionId)
                .eq(WrongQuestion::getStudentId, studentId));
        if (rows.isEmpty()) {
            return 0;
        }
        Set<Long> questionIds = rows.stream().map(WrongQuestion::getPracticeQuestionId)
                .collect(Collectors.toSet());
        Map<Long, QuestionDetail> details = questions.details(questionIds);
        return questionIds.stream()
                .map(details::get)
                .filter(detail -> detail != null && ON_SHELF.equals(detail.status()))
                .count();
    }
}
