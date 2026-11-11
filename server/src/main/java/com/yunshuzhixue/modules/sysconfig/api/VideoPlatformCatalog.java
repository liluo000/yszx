package com.yunshuzhixue.modules.sysconfig.api;

import java.util.List;
import java.util.Optional;

/** 视频平台及其允许的域名，供课程校验视频链接（需求 7.8） */
public interface VideoPlatformCatalog {

    record PlatformInfo(Long id, String name, boolean enabled, List<String> domains) {
    }

    Optional<PlatformInfo> find(Long id);

    List<PlatformInfo> all();
}
