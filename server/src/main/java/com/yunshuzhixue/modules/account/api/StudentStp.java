package com.yunshuzhixue.modules.account.api;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 学员账号体系，与后台账号体系相互隔离（02.整体架构设计.md 5.6）。
 *
 * <p>7 天内有访问就保持登录，允许多个设备同时登录；令牌放在 Authorization: Bearer 请求头。</p>
 */
public final class StudentStp {

    public static final String LOGIN_TYPE = "student";

    /** 7 天无访问失效（需求 5.1.6） */
    public static final long ACTIVE_TIMEOUT_SECONDS = 7L * 24 * 60 * 60;

    /** 令牌最长有效期 30 天，实际由 7 天无访问失效控制 */
    public static final long TIMEOUT_SECONDS = 30L * 24 * 60 * 60;

    public static final StpLogic LOGIC = new StpLogic(LOGIN_TYPE).setConfig(config());

    private StudentStp() {
    }

    private static SaTokenConfig config() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadCookie(false);
        config.setIsReadBody(false);
        config.setIsConcurrent(true);
        config.setIsShare(false);
        config.setTimeout(TIMEOUT_SECONDS);
        config.setActiveTimeout(ACTIVE_TIMEOUT_SECONDS);
        config.setIsPrint(false);
        return config;
    }
}
