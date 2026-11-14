package com.yunshuzhixue.modules.help.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.sysconfig.api.AgreementCatalog;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 协议查看（需求 5.1.1、5.11，S-HLP-01）：正文由 M03 维护，本模块只做展示 */
@Service
public class StudentAgreementService {

    private final AgreementCatalog catalog;

    public StudentAgreementService(AgreementCatalog catalog) {
        this.catalog = catalog;
    }

    public AgreementDetail latest(String agreementType) {
        AgreementCatalog.Latest latest = catalog.latest(agreementType);
        if (latest == null) {
            // 类型非法或后台尚未发布：对学员统一按"不存在"处理
            throw new BusinessException(ErrorCode.NOT_FOUND, "协议内容尚未发布");
        }
        return new AgreementDetail(latest.agreementType(), latest.versionNo(), latest.content(),
                latest.publishedAt());
    }

    public record AgreementDetail(String agreementType, int versionNo, String content, Instant publishedAt) {
    }
}
