package com.yunshuzhixue.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.iam.api.AdminStp;
import com.yunshuzhixue.modules.iam.domain.AdminRole;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台登录、退出、修改密码（04.API接口设计.md A-IAM-01~04）。
 */
@Service
public class AdminAuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter limiter;
    private final OperationLogRecorder operationLog;

    /** 账号不存在时也做一次哈希比对，使响应时间一致，避免据此判断账号是否存在 */
    private final String dummyHash;

    public AdminAuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder,
                            LoginAttemptLimiter limiter, OperationLogRecorder operationLog) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.operationLog = operationLog;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record LoginResult(String token, SysUser user) {
    }

    @Transactional
    public LoginResult login(String rawAccount, String password) {
        String account = rawAccount.trim();
        if (limiter.isLocked(account)) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                    "密码连续输错 5 次，账号已锁定 15 分钟，可联系超级管理员重置密码");
        }
        SysUser user = findByAccount(account);
        boolean matched = passwordEncoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matched) {
            int remaining = limiter.recordFailure(account);
            if (remaining == 0) {
                throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                        "密码连续输错 5 次，账号已锁定 15 分钟，可联系超级管理员重置密码");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "账号或密码错误，还可尝试 " + remaining + " 次", List.of(Map.of("remaining_attempts", remaining)));
        }
        if (SysUser.STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "该账号已停用，无法登录后台");
        }
        limiter.reset(account);

        AdminStp.LOGIC.login(user.getId());
        AdminStp.LOGIC.getSession().set(AdminAuthInterceptor.SESSION_MUST_CHANGE_PASSWORD,
                Boolean.TRUE.equals(user.getMustChangePassword()));

        user.setLastLoginAt(UtcTime.now());
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(user.getLastLoginAt());
        sysUserMapper.updateById(update);

        record(user, "后台登录");
        return new LoginResult(AdminStp.LOGIC.getTokenValue(), user);
    }

    public void logout() {
        AdminStp.LOGIC.logout();
    }

    public SysUser currentUser() {
        SysUser user = sysUserMapper.selectById(AdminStp.LOGIC.getLoginIdAsLong());
        if (user == null || SysUser.STATUS_DISABLED.equals(user.getStatus())) {
            AdminStp.LOGIC.logout();
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public List<String> permissionsOf(SysUser user) {
        return AdminRole.of(user.getRole()).permissions();
    }

    /** 修改自己的密码：校验原密码；修改后所有设备退出，需重新登录（需求 6.9） */
    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        update.setMustChangePassword(false);
        update.setPasswordChangedAt(UtcTime.now());
        sysUserMapper.updateById(update);

        record(user, "修改密码");
        AdminStp.LOGIC.logout(user.getId());
        limiter.reset(user.getAccount());
    }

    private SysUser findByAccount(String account) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, account));
    }

    private void record(SysUser user, String action) {
        operationLog.record(new OperationLogCommand(user.getId(), user.getName(), user.getRole(), AuditModules.ACCOUNT, action,
                "sys_user", user.getId(), "账号 " + user.getAccount(), ClientIps.current()));
    }
}
