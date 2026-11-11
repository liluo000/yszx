package com.yunshuzhixue.modules.sysconfig.api;

import tools.jackson.databind.JsonNode;

/** 读取系统配置，供其他模块使用（如每日次数上限、达标线） */
public interface SysConfigReader {

    JsonNode get(String configKey);

    int getInt(String configKey);
}
