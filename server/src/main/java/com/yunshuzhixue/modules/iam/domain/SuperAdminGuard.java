package com.yunshuzhixue.modules.iam.domain;

/**
 * 必须保留至少 1 个启用的超级管理员（需求 6.9）。
 */
public final class SuperAdminGuard {

    private SuperAdminGuard() {
    }

    /**
     * 判断一次修改是否会让系统失去最后一个启用的超级管理员。
     *
     * @param enabledSuperAdmins 修改前启用的超级管理员数量
     * @param targetIsEnabledSuperAdmin 被修改的账号修改前是否为启用的超级管理员
     * @param remainsEnabledSuperAdmin 被修改的账号修改后是否仍为启用的超级管理员
     */
    public static boolean removesLastSuperAdmin(long enabledSuperAdmins, boolean targetIsEnabledSuperAdmin,
                                                boolean remainsEnabledSuperAdmin) {
        return targetIsEnabledSuperAdmin && !remainsEnabledSuperAdmin && enabledSuperAdmins <= 1;
    }
}
