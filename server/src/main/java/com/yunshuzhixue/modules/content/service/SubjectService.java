package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.content.entity.CourseSubject;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseSubjectMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 学科管理（需求 6.1，A-CNT-01~05）：名称唯一，有课程关联时不能删除 */
@Service
public class SubjectService {

    public record SubjectItem(Subject subject, long courseCount) {
    }

    public record SortItem(Long id, Integer sortNo) {
    }

    private final SubjectMapper subjectMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final OperationLogRecorder recorder;

    public SubjectService(SubjectMapper subjectMapper, CourseSubjectMapper courseSubjectMapper,
                          OperationLogRecorder recorder) {
        this.subjectMapper = subjectMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.recorder = recorder;
    }

    /** 全部学科按排序号排列，课程数一次分组统计，避免逐个查询 */
    public List<SubjectItem> list() {
        List<Subject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .orderByAsc(Subject::getSortNo).orderByAsc(Subject::getId));
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : courseSubjectMapper.selectMaps(new QueryWrapper<CourseSubject>()
                .select("subject_id", "COUNT(*) AS course_count").groupBy("subject_id"))) {
            counts.put(((Number) row.get("subject_id")).longValue(), ((Number) row.get("course_count")).longValue());
        }
        return subjects.stream().map(s -> new SubjectItem(s, counts.getOrDefault(s.getId(), 0L))).toList();
    }

    @Transactional
    public Subject create(CurrentAdmin operator, String name, String intro, Integer sortNo) {
        String trimmed = name.trim();
        ensureNameFree(trimmed, null);
        Subject subject = new Subject();
        subject.setName(trimmed);
        subject.setIntro(blankToNull(intro));
        subject.setSortNo(sortNo);
        try {
            subjectMapper.insert(subject);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
        }
        log(operator, "新增学科", subject.getId(), "学科 " + trimmed);
        return subject;
    }

    /** 只修改传入的字段；简介传空字符串表示清空 */
    @Transactional
    public void update(CurrentAdmin operator, Long id, String name, String intro, Integer sortNo) {
        Subject subject = require(id);
        String newName = name == null ? subject.getName() : name.trim();
        if (name != null) {
            ensureNameFree(newName, id);
        }
        LambdaUpdateWrapper<Subject> update = new LambdaUpdateWrapper<Subject>().eq(Subject::getId, id)
                .set(name != null, Subject::getName, newName)
                .set(intro != null, Subject::getIntro, blankToNull(intro))
                .set(sortNo != null, Subject::getSortNo, sortNo);
        if (name != null || intro != null || sortNo != null) {
            try {
                subjectMapper.update(null, update);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
            }
        }
        log(operator, "编辑学科", id, "学科 " + newName);
    }

    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        Subject subject = require(id);
        // 课程可属于多个学科：只要有课程关联本学科就不能删除，需先在课程中去掉该学科
        long courses = courseSubjectMapper.selectCount(
                new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getSubjectId, id));
        if (courses > 0) {
            String reason = "学科「" + subject.getName() + "」下仍有 " + courses + " 门课程，不能删除";
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, reason,
                    List.of(new FieldViolation("course", reason)));
        }
        subjectMapper.deleteById(id);
        log(operator, "删除学科", id, "学科 " + subject.getName());
    }

    @Transactional
    public void sort(CurrentAdmin operator, List<SortItem> items) {
        Set<Long> ids = new HashSet<>();
        for (SortItem item : items) {
            if (!ids.add(item.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序中有重复的学科");
            }
        }
        if (subjectMapper.selectCount(new LambdaQueryWrapper<Subject>().in(Subject::getId, ids)) != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        for (SortItem item : items) {
            subjectMapper.update(null, new LambdaUpdateWrapper<Subject>()
                    .eq(Subject::getId, item.id()).set(Subject::getSortNo, item.sortNo()));
        }
        log(operator, "调整学科排序", null, items.size() + " 个学科");
    }

    private Subject require(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return subject;
    }

    /** 与数据库排序规则一致，不区分大小写 */
    private void ensureNameFree(String name, Long selfId) {
        Subject existing = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>().eq(Subject::getName, name));
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw new BusinessException(ErrorCode.SUBJECT_NAME_EXISTS);
        }
    }

    private void log(CurrentAdmin operator, String action, Long targetId, String label) {
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.SUBJECT,
                action, "subject", targetId, label, ClientIps.current()));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
