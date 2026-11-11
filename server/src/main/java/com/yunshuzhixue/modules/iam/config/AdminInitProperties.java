package com.yunshuzhixue.modules.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始超级管理员。系统中没有任何后台账号时，启动时按此配置创建，首次登录必须修改密码。
 * 本地读取 deploy/dev/.env，线上读取环境变量。
 */
@ConfigurationProperties(prefix = "yunshu.admin.init")
public record AdminInitProperties(String account, String password, String name, String phone) {
}
