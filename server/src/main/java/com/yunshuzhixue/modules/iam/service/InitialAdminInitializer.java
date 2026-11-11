package com.yunshuzhixue.modules.iam.service;

import com.yunshuzhixue.modules.iam.config.AdminInitProperties;
import com.yunshuzhixue.modules.iam.domain.AdminRole;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 系统中没有任何后台账号时，按配置创建第一个超级管理员，首次登录必须修改密码。
 * 已有账号时不做任何操作，所以配置只在首次部署时生效。
 */
@Component
class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminInitProperties properties;

    InitialAdminInitializer(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, AdminInitProperties properties) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sysUserMapper.selectCount(null) > 0) {
            return;
        }
        if (!StringUtils.hasText(properties.account()) || !StringUtils.hasText(properties.password())
                || !StringUtils.hasText(properties.phone())) {
            log.warn("系统中没有后台账号，且未配置初始超级管理员（ADMIN_INIT_ACCOUNT、ADMIN_INIT_PASSWORD、ADMIN_INIT_PHONE），无法登录后台");
            return;
        }
        int length = properties.password().length();
        if (length < 6 || length > 20) {
            throw new IllegalStateException("初始超级管理员的密码长度必须为 6~20 位");
        }
        SysUser admin = new SysUser();
        admin.setAccount(properties.account().trim());
        admin.setName(StringUtils.hasText(properties.name()) ? properties.name() : "超级管理员");
        admin.setPhone(properties.phone());
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setRole(AdminRole.SUPER_ADMIN.value());
        admin.setStatus(SysUser.STATUS_ENABLED);
        admin.setMustChangePassword(true);
        sysUserMapper.insert(admin);
        log.info("已创建初始超级管理员，账号：{}，首次登录必须修改密码", admin.getAccount());
    }
}
