package com.yunshuzhixue.modules.content.domain;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** 视频链接校验：http/https 链接，域名等于平台允许的域名或是其子域名（需求 6.2、7.8） */
public final class VideoUrls {

    private VideoUrls() {
    }

    public static boolean belongsTo(String url, List<String> domains) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        return domains.stream().map(d -> d.toLowerCase(Locale.ROOT))
                .anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }

    static String hostOf(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
