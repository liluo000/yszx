package com.yunshuzhixue.modules.sysconfig.service;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.sysconfig.api.AgreementCatalog;
import com.yunshuzhixue.modules.sysconfig.entity.AgreementVersion;
import org.springframework.stereotype.Service;

/** 协议目录实现：复用后台协议服务读取最新版本 */
@Service
public class AgreementCatalogService implements AgreementCatalog {

    private final AgreementService agreements;

    public AgreementCatalogService(AgreementService agreements) {
        this.agreements = agreements;
    }

    @Override
    public Latest latest(String agreementType) {
        if (!AgreementService.TYPES.contains(agreementType)) {
            return null;
        }
        AgreementVersion version = agreements.latest(agreementType);
        if (version == null) {
            return null;
        }
        return new Latest(version.getAgreementType(), version.getVersionNo(), version.getContent(),
                UtcTime.toInstant(version.getPublishedAt()));
    }
}
