package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.api.SensitiveWordsChangedEvent;
import com.yunshuzhixue.modules.sysconfig.entity.SensitiveWord;
import com.yunshuzhixue.modules.sysconfig.mapper.SensitiveWordMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 敏感词库（需求 7.6，A-CFG-05~08） */
@Service
public class SensitiveWordService {

    public static final int MAX_LENGTH = 100;

    public record ImportResult(int successCount, int failureCount, List<Map<String, Object>> failures) {
    }

    private final SensitiveWordMapper mapper;
    private final ApplicationEventPublisher events;
    private final ConfigAudit audit;

    public SensitiveWordService(SensitiveWordMapper mapper, ApplicationEventPublisher events, ConfigAudit audit) {
        this.mapper = mapper;
        this.events = events;
        this.audit = audit;
    }

    public PageResult<SensitiveWord> list(PageQuery page, String keyword) {
        LambdaQueryWrapper<SensitiveWord> query = new LambdaQueryWrapper<SensitiveWord>()
                .like(StringUtils.hasText(keyword), SensitiveWord::getWord, keyword);
        long total = mapper.selectCount(query);
        query.orderByDesc(SensitiveWord::getId).last(page.limitClause());
        return new PageResult<>(mapper.selectList(query), total, page.page(), page.pageSize());
    }

    @Transactional
    public SensitiveWord add(CurrentAdmin operator, String rawWord) {
        String word = rawWord.trim();
        if (exists(word)) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD_EXISTS);
        }
        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setCreatedBySysUserId(operator.id());
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD_EXISTS);
        }
        audit.record(operator, "新增敏感词", "sensitive_word", entity.getId(), word);
        events.publishEvent(new SensitiveWordsChangedEvent());
        return entity;
    }

    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        SensitiveWord word = mapper.selectById(id);
        if (word == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        mapper.deleteById(id);
        audit.record(operator, "删除敏感词", "sensitive_word", id, word.getWord());
        events.publishEvent(new SensitiveWordsChangedEvent());
    }

    /** 批量导入：第 1 列为敏感词，第 1 行为表头。部分行失败不影响成功的行 */
    @Transactional
    public ImportResult importWords(CurrentAdmin operator, InputStream in) {
        List<ExcelSheets.SheetRow> rows;
        try {
            rows = ExcelSheets.readRows(in, 1);
        } catch (ExcelSheets.TooManyRowsException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "单次最多导入 " + ExcelSheets.MAX_IMPORT_ROWS + " 行");
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件，请使用 xlsx 格式的导入模板");
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        Set<String> inserted = new HashSet<>();
        for (ExcelSheets.SheetRow row : rows) {
            String word = row.cell(0);
            String reason = null;
            if (word.length() > MAX_LENGTH) {
                reason = "超过 " + MAX_LENGTH + " 个字符";
            } else if (seen.containsKey(word.toLowerCase())) {
                reason = "与第 " + seen.get(word.toLowerCase()) + " 行重复";
            } else if (exists(word)) {
                reason = "敏感词已存在";
            }
            seen.putIfAbsent(word.toLowerCase(), row.rowNo());
            if (reason != null) {
                failures.add(Map.of("row_no", row.rowNo(), "reason", reason));
                continue;
            }
            SensitiveWord entity = new SensitiveWord();
            entity.setWord(word);
            entity.setCreatedBySysUserId(operator.id());
            mapper.insert(entity);
            inserted.add(word);
        }
        if (!inserted.isEmpty()) {
            audit.record(operator, "导入敏感词", "sensitive_word", null, "成功 " + inserted.size() + " 个");
            events.publishEvent(new SensitiveWordsChangedEvent());
        }
        return new ImportResult(inserted.size(), failures.size(), failures);
    }

    /** 与数据库排序规则一致，不区分大小写 */
    private boolean exists(String word) {
        return mapper.selectCount(new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, word)) > 0;
    }
}
