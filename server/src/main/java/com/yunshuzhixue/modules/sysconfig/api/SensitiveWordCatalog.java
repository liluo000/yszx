package com.yunshuzhixue.modules.sysconfig.api;

import java.util.List;

/** 敏感词库只读目录（M14 内容安全通过公开接口读取，避免跨模块访问实体） */
public interface SensitiveWordCatalog {

    List<String> words();
}
