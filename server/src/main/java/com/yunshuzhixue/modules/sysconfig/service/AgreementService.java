package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.api.AgreementPublishedEvent;
import com.yunshuzhixue.modules.sysconfig.entity.AgreementVersion;
import com.yunshuzhixue.modules.sysconfig.mapper.AgreementVersionMapper;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 协议文本（需求 7.5，A-CFG-03、04）。每次保存发布一个新版本，历史版本保留 */
@Service
public class AgreementService {

    public static final List<String> TYPES = List.of("user_agreement", "privacy_policy");

    private final AgreementVersionMapper mapper;
    private final ApplicationEventPublisher events;
    private final ConfigAudit audit;

    public AgreementService(AgreementVersionMapper mapper, ApplicationEventPublisher events, ConfigAudit audit) {
        this.mapper = mapper;
        this.events = events;
        this.audit = audit;
    }

    /** 每类协议的最新版本，尚未发布时为 null */
    public AgreementVersion latest(String type) {
        return mapper.selectOne(new LambdaQueryWrapper<AgreementVersion>()
                .eq(AgreementVersion::getAgreementType, type)
                .orderByDesc(AgreementVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    @Transactional
    public AgreementVersion publish(CurrentAdmin operator, String type, String content) {
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        AgreementVersion current = latest(type);
        AgreementVersion version = new AgreementVersion();
        version.setAgreementType(type);
        version.setVersionNo(current == null ? 1 : current.getVersionNo() + 1);
        version.setContent(content);
        version.setPublishedBySysUserId(operator.id());
        version.setPublishedAt(UtcTime.now());
        mapper.insert(version);
        audit.record(operator, "发布协议", "agreement_version", version.getId(),
                ("user_agreement".equals(type) ? "用户协议" : "隐私政策") + " 第 " + version.getVersionNo() + " 版");
        events.publishEvent(new AgreementPublishedEvent(type, version.getVersionNo()));
        return version;
    }
}
