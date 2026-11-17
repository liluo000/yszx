package com.yunshuzhixue.modules.sysconfig.service;

import com.yunshuzhixue.modules.sysconfig.api.HelpDirectory;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** 帮助中心内容（需求 5.11）：常见问题取 faq 表，联系方式取 help.contact 配置 */
@Service
public class HelpDirectoryService implements HelpDirectory {

    private static final String CONTACT_KEY = "help.contact";

    private final JdbcTemplate jdbc;
    private final SysConfigReader configs;

    public HelpDirectoryService(JdbcTemplate jdbc, SysConfigReader configs) {
        this.jdbc = jdbc;
        this.configs = configs;
    }

    @Override
    public List<Faq> faqs() {
        List<Faq> items = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id, question, answer FROM faq ORDER BY sort_no, id")) {
            items.add(new Faq(((Number) row.get("id")).longValue(), String.valueOf(row.get("question")),
                    String.valueOf(row.get("answer"))));
        }
        return items;
    }

    @Override
    public Contact contact() {
        JsonNode node = configs.get(CONTACT_KEY);
        return new Contact(text(node, "wechat_qrcode_url"), text(node, "email"), text(node, "phone"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
