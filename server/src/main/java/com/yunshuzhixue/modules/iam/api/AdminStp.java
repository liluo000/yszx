package com.yunshuzhixue.modules.iam.api;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 后台账号体系，与学员账号体系相互隔离（02.整体架构设计.md 5.6）。
 *
 * <p>8 小时无操作自动失效；允许多设备同时登录；令牌放在 Authorization: Bearer 请求头。</p>
 */
public final class AdminStp {

    public static final String LOGIN_TYPE = "admin";

    /** 8 小时无操作失效（需求 5.1.6） */
    public static final long ACTIVE_TIMEOUT_SECONDS = 8 * 60 * 60;

    /** 令牌最长有效期 30 天，实际由 8 小时无操作失效控制 */
    public static final long TIMEOUT_SECONDS = 30L * 24 * 60 * 60;

    public static final StpLogic LOGIC = new StpLogic(LOGIN_TYPE).setConfig(config());

    private AdminStp() {
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
