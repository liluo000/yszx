package com.yunshuzhixue.modules.iam.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import java.time.Instant;

/** 后台账号（04.API接口设计.md 公共对象 SysUserView） */
public record SysUserView(Long id, String account, String name, String phone, String role, String status,
                          Instant lastLoginAt, Instant createdAt) {

    public static SysUserView of(SysUser u) {
        return new SysUserView(u.getId(), u.getAccount(), u.getName(), u.getPhone(), u.getRole(), u.getStatus(),
                UtcTime.toInstant(u.getLastLoginAt()), UtcTime.toInstant(u.getCreatedAt()));
    }
}
