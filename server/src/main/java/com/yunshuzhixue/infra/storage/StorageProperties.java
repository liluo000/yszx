package com.yunshuzhixue.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对象存储配置，本地读取 deploy/dev/.env，线上读取环境变量 */
@ConfigurationProperties(prefix = "yunshu.storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey,
                                String publicBaseUrl, boolean pathStyle) {
}
