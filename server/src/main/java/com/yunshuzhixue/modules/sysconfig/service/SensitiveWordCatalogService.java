package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.sysconfig.api.SensitiveWordCatalog;
import com.yunshuzhixue.modules.sysconfig.entity.SensitiveWord;
import com.yunshuzhixue.modules.sysconfig.mapper.SensitiveWordMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 词库读取：M14 在进程内缓存，词库变更事件后重新加载 */
@Service
public class SensitiveWordCatalogService implements SensitiveWordCatalog {

    private final SensitiveWordMapper mapper;

    public SensitiveWordCatalogService(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> words() {
        return mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                        .select(SensitiveWord::getWord)
                        .orderByAsc(SensitiveWord::getId))
                .stream().map(SensitiveWord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
