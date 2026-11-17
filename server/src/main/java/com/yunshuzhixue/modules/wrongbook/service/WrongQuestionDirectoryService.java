package com.yunshuzhixue.modules.wrongbook.service;

import com.yunshuzhixue.modules.practice.api.WrongQuestionDirectory;
import com.yunshuzhixue.modules.wrongbook.mapper.WrongQuestionMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 把错题记录 ID 解析成题目 ID，供 M09 错题重做使用 */
@Service
public class WrongQuestionDirectoryService implements WrongQuestionDirectory {

    private final WrongQuestionMapper mapper;

    public WrongQuestionDirectoryService(WrongQuestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<Long, Long> questionIdsOf(long studentId, Collection<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (com.yunshuzhixue.modules.wrongbook.entity.WrongQuestion row : mapper.selectBatchIds(recordIds)) {
            if (row.getStudentId() == studentId) {
                result.put(row.getId(), row.getPracticeQuestionId());
            }
        }
        return result;
    }
}
