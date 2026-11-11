package com.yunshuzhixue.modules.iam.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminStp;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.iam.domain.AdminRole;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
class AdminAccessService implements AdminAccess {

    private final SysUserMapper sysUserMapper;

    AdminAccessService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public CurrentAdmin require(String permission) {
        return requireAny(permission);
    }

    @Override
    public CurrentAdmin requireAny(String... permissions) {
        SysUser user = load();
        var granted = AdminRole.of(user.getRole()).permissions();
        if (Arrays.stream(permissions).noneMatch(granted::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toCurrent(user);
    }

    @Override
    public CurrentAdmin current() {
        return toCurrent(load());
    }

    private SysUser load() {
        SysUser user = sysUserMapper.selectById(AdminStp.LOGIC.getLoginIdAsLong());
        if (user == null || SysUser.STATUS_DISABLED.equals(user.getStatus())) {
            AdminStp.LOGIC.logout();
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    private static CurrentAdmin toCurrent(SysUser u) {
        return new CurrentAdmin(u.getId(), u.getAccount(), u.getName(), u.getRole());
    }
}
