package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import com.yunshuzhixue.modules.sysconfig.domain.ConfigDefinitions;
import com.yunshuzhixue.modules.sysconfig.entity.SysConfig;
import com.yunshuzhixue.modules.sysconfig.mapper.SysConfigMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 系统配置项（A-CFG-01、02）。修改即时生效：读取时直接查库，已开始的场次在开始时固定参数（第一阶段 Q21） */
@Service
public class ConfigService implements SysConfigReader {

    public record ConfigItem(String configKey, JsonNode configValue, String description, SysConfig row) {
    }

    private final SysConfigMapper mapper;
    private final JsonMapper json;
    private final ConfigAudit audit;

    public ConfigService(SysConfigMapper mapper, JsonMapper json, ConfigAudit audit) {
        this.mapper = mapper;
        this.json = json;
        this.audit = audit;
    }

    public List<ConfigItem> list() {
        Map<String, SysConfig> rows = mapper.selectList(null).stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, Function.identity()));
        return ConfigDefinitions.all().stream()
                .filter(d -> rows.containsKey(d.key()))
                .map(d -> {
                    SysConfig row = rows.get(d.key());
                    return new ConfigItem(d.key(), json.readTree(row.getConfigValue()), d.description(), row);
                })
                .toList();
    }

    @Transactional
    public ConfigItem update(CurrentAdmin operator, String key, JsonNode value) {
        ConfigDefinitions.Definition definition = ConfigDefinitions.find(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String error = definition.validator().validate(value);
        if (error != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, error,
                    List.of(new FieldViolation("config_value", error)));
        }
        SysConfig row = row(key);
        SysConfig update = new SysConfig();
        update.setId(row.getId());
        update.setConfigValue(json.writeValueAsString(value));
        update.setUpdatedBySysUserId(operator.id());
        mapper.updateById(update);
        // 记录修改前后的值，便于追溯（原型：××由 A 改为 B）
        audit.record(operator, "修改配置", "sys_config", row.getId(),
                definition.description() + "：" + row.getConfigValue() + " → " + update.getConfigValue());
        SysConfig saved = mapper.selectById(row.getId());
        return new ConfigItem(key, json.readTree(saved.getConfigValue()), definition.description(), saved);
    }

    @Override
    public JsonNode get(String configKey) {
        return json.readTree(row(configKey).getConfigValue());
    }

    @Override
    public int getInt(String configKey) {
        return get(configKey).intValue();
    }

    private SysConfig row(String key) {
        SysConfig row = mapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}
