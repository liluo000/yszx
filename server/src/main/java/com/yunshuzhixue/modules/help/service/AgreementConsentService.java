package com.yunshuzhixue.modules.help.service;

import com.yunshuzhixue.common.agreement.AgreementConsentGateway;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.sysconfig.api.AgreementCatalog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协议同意记录（需求 5.1.2，S-HLP-02）。
 *
 * <p>记录表归 M17；M01 计算 pending_agreements 与本模块之外的拦截器通过
 * {@link AgreementConsentGateway} 读取，不跨模块访问表。</p>
 */
@Service
public class AgreementConsentService implements AgreementConsentGateway {

    private static final List<String> TYPES = List.of("user_agreement", "privacy_policy");

    private final JdbcTemplate jdbc;
    private final AgreementCatalog catalog;

    public AgreementConsentService(JdbcTemplate jdbc, AgreementCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Override
    public List<Pending> pending(long studentId) {
        List<Pending> items = new ArrayList<>();
        for (String type : TYPES) {
            AgreementCatalog.Latest latest = catalog.latest(type);
            if (latest == null) {
                // 后台尚未发布该类协议时不需要同意
                continue;
            }
            int agreed = agreedVersion(studentId, type);
            if (agreed < latest.versionNo()) {
                items.add(new Pending(type, latest.versionNo()));
            }
        }
        return items;
    }

    /** S-HLP-02：提交的每一项都必须是最新版本 */
    @Transactional
    public void accept(long studentId, List<Consent> consents) {
        if (consents == null || consents.isEmpty() || consents.size() > TYPES.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请提交需要同意的协议");
        }
        for (Consent consent : consents) {
            if (!TYPES.contains(consent.agreementType())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "协议类型不正确");
            }
            AgreementCatalog.Latest latest = catalog.latest(consent.agreementType());
            if (latest != null && consent.versionNo() != latest.versionNo()) {
                throw new BusinessException(ErrorCode.AGREEMENT_VERSION_OUTDATED);
            }
            record(studentId, consent.agreementType(),
                    latest == null ? consent.versionNo() : latest.versionNo());
        }
    }

    /** 注册与验证码自动注册时写入同意记录（M01 发布事件，本模块订阅） */
    @Transactional
    public void recordRegistration(long studentId, List<Consent> consents) {
        for (Consent consent : consents) {
            if (TYPES.contains(consent.agreementType())) {
                record(studentId, consent.agreementType(), consent.versionNo());
            }
        }
    }

    private void record(long studentId, String agreementType, int versionNo) {
        jdbc.update("INSERT INTO student_agreement_consent (student_id, agreement_type, version_no, agreed_at)"
                + " VALUES (?, ?, ?, NOW(3))"
                + " ON DUPLICATE KEY UPDATE agreed_at = NOW(3)", studentId, agreementType, versionNo);
    }

    private int agreedVersion(long studentId, String agreementType) {
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0)"
                        + " FROM student_agreement_consent WHERE student_id = ? AND agreement_type = ?",
                Integer.class, studentId, agreementType);
        return version == null ? 0 : version;
    }

    /** 提交的一类协议同意项 */
    public record Consent(String agreementType, int versionNo) {
    }
}
