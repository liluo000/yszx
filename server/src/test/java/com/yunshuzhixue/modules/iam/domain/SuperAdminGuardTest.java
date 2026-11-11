package com.yunshuzhixue.modules.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuperAdminGuardTest {

    @Test
    void blocksRemovingTheOnlyEnabledSuperAdmin() {
        assertThat(SuperAdminGuard.removesLastSuperAdmin(1, true, false)).isTrue();
    }

    @Test
    void allowsWhenAnotherSuperAdminRemains() {
        assertThat(SuperAdminGuard.removesLastSuperAdmin(2, true, false)).isFalse();
    }

    @Test
    void allowsChangesThatKeepTheSuperAdmin() {
        assertThat(SuperAdminGuard.removesLastSuperAdmin(1, true, true)).isFalse();
        assertThat(SuperAdminGuard.removesLastSuperAdmin(1, false, false)).isFalse();
    }
}
