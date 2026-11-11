package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.entity.Faq;
import com.yunshuzhixue.modules.sysconfig.mapper.FaqMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 帮助中心常见问题（需求 7.7，A-CFG-13~17） */
@Service
public class FaqService {

    public record SortItem(Long id, Integer sortNo) {
    }

    private final FaqMapper mapper;
    private final ConfigAudit audit;

    public FaqService(FaqMapper mapper, ConfigAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public List<Faq> list() {
        return mapper.selectList(new LambdaQueryWrapper<Faq>().orderByAsc(Faq::getSortNo).orderByAsc(Faq::getId));
    }

    @Transactional
    public Faq create(CurrentAdmin operator, String question, String answer, Integer sortNo) {
        Faq faq = new Faq();
        faq.setQuestion(question.trim());
        faq.setAnswer(answer.trim());
        faq.setSortNo(sortNo);
        mapper.insert(faq);
        audit.record(operator, "新增常见问题", "faq", faq.getId(), faq.getQuestion());
        return mapper.selectById(faq.getId());
    }

    @Transactional
    public Faq update(CurrentAdmin operator, Long id, String question, String answer, Integer sortNo) {
        require(id);
        Faq update = new Faq();
        update.setId(id);
        update.setQuestion(question == null ? null : question.trim());
        update.setAnswer(answer == null ? null : answer.trim());
        update.setSortNo(sortNo);
        mapper.updateById(update);
        Faq saved = mapper.selectById(id);
        audit.record(operator, "编辑常见问题", "faq", id, saved.getQuestion());
        return saved;
    }

    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        Faq faq = require(id);
        mapper.deleteById(id);
        audit.record(operator, "删除常见问题", "faq", id, faq.getQuestion());
    }

    @Transactional
    public void sort(CurrentAdmin operator, List<SortItem> items) {
        for (SortItem item : items) {
            require(item.id());
            Faq update = new Faq();
            update.setId(item.id());
            update.setSortNo(item.sortNo());
            mapper.updateById(update);
        }
        audit.record(operator, "调整常见问题排序", "faq", null, items.size() + " 条");
    }

    private Faq require(Long id) {
        Faq faq = mapper.selectById(id);
        if (faq == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return faq;
    }
}
