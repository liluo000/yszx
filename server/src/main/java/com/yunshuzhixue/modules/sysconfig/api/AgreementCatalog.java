package com.yunshuzhixue.modules.sysconfig.api;

import java.time.Instant;

/**
 * 协议最新版本目录（需求 7.5）。协议正文由 M03 维护，其他模块只通过本接口读取，
 * 不直接访问 agreement_version 表（02.整体架构设计.md 4.2）。
 */
public interface AgreementCatalog {

    /** 某类协议的最新版本；后台尚未发布时返回 null */
    Latest latest(String agreementType);

    /** 协议最新版本：versionNo 用于校验学员提交的同意版本，content 用于展示正文 */
    record Latest(String agreementType, int versionNo, String content, Instant publishedAt) {
    }
}
