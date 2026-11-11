package com.yunshuzhixue.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.iam.api.AdminStp;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.iam.domain.AdminRole;
import com.yunshuzhixue.modules.iam.domain.SuperAdminGuard;
import com.yunshuzhixue.modules.iam.domain.TemporaryPasswords;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 系统用户管理（需求 6.9，A-IAM-05~09），仅超级管理员可用 */
@Service
public class SysUserService {

    private final SysUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter limiter;
    private final OperationLogRecorder operationLog;

    public SysUserService(SysUserMapper mapper, PasswordEncoder passwordEncoder, LoginAttemptLimiter limiter,
                          OperationLogRecorder operationLog) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.operationLog = operationLog;
    }

    public record Created(SysUser user, String temporaryPassword) {
    }

    public PageResult<SysUser> list(PageQuery page, String role, String status) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .eq(StringUtils.hasText(status), SysUser::getStatus, status);
        long total = mapper.selectCount(query);
        query.orderByDesc(SysUser::getCreatedAt).orderByDesc(SysUser::getId).last(page.limitClause());
        return new PageResult<>(mapper.selectList(query), total, page.page(), page.pageSize());
    }

    /** 新建账号：系统生成临时密码，只返回一次，登录后必须修改（第三阶段确认） */
    @Transactional
    public Created create(CurrentAdmin operator, String account, String name, String phone, String role) {
        AdminRole.of(role);
        String trimmed = account.trim();
        if (mapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, trimmed)) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        String temporaryPassword = TemporaryPasswords.generate();
        SysUser user = new SysUser();
        user.setAccount(trimmed);
        user.setName(name.trim());
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(SysUser.STATUS_ENABLED);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        try {
            mapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        record(operator, "新建账号", mapper.selectById(user.getId()));
        return new Created(mapper.selectById(user.getId()), temporaryPassword);
    }

    @Transactional
    public SysUser update(CurrentAdmin operator, Long id, String name, String phone, String role) {
        SysUser user = get(id);
        if (role != null) {
            AdminRole.of(role);
            boolean wasEnabledSuper = isEnabledSuperAdmin(user);
            boolean staysEnabledSuper = SysUser.STATUS_ENABLED.equals(user.getStatus())
                    && AdminRole.SUPER_ADMIN.value().equals(role);
            if (SuperAdminGuard.removesLastSuperAdmin(countEnabledSuperAdmins(), wasEnabledSuper, staysEnabledSuper)) {
                throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
            }
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setName(name == null ? null : name.trim());
        update.setPhone(phone);
        update.setRole(role);
        mapper.updateById(update);
        SysUser updated = get(id);
        record(operator, "编辑账号", updated);
        return updated;
    }

    /** 启用或停用：不能停用自己；必须保留 1 个启用的超级管理员；停用后立即退出登录 */
    @Transactional
    public SysUser changeStatus(CurrentAdmin operator, Long id, String status) {
        SysUser user = get(id);
        boolean disable = SysUser.STATUS_DISABLED.equals(status);
        if (disable && id.equals(operator.id())) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        if (disable && SuperAdminGuard.removesLastSuperAdmin(countEnabledSuperAdmins(), isEnabledSuperAdmin(user), false)) {
            throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        mapper.updateById(update);
        if (disable) {
            AdminStp.LOGIC.logout(id);
        }
        SysUser updated = get(id);
        record(operator, disable ? "停用账号" : "启用账号", updated);
        return updated;
    }

    /** 重置密码：生成临时密码，对方立即退出登录并解除错误锁定，登录后必须修改密码 */
    @Transactional
    public String resetPassword(CurrentAdmin operator, Long id) {
        SysUser user = get(id);
        String temporaryPassword = TemporaryPasswords.generate();
        SysUser update = new SysUser();
        update.setId(id);
        update.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        update.setMustChangePassword(true);
        mapper.updateById(update);
        AdminStp.LOGIC.logout(id);
        limiter.reset(user.getAccount());
        record(operator, "重置密码", user);
        return temporaryPassword;
    }

    private SysUser get(Long id) {
        SysUser user = mapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    private long countEnabledSuperAdmins() {
        return mapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, AdminRole.SUPER_ADMIN.value())
                .eq(SysUser::getStatus, SysUser.STATUS_ENABLED));
    }

    private static boolean isEnabledSuperAdmin(SysUser user) {
        return AdminRole.SUPER_ADMIN.value().equals(user.getRole()) && SysUser.STATUS_ENABLED.equals(user.getStatus());
    }

    private void record(CurrentAdmin operator, String action, SysUser target) {
        operationLog.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(),
                AuditModules.SYS_USER, action, "sys_user", target.getId(), "账号 " + target.getAccount(),
                ClientIps.current()));
    }
}
