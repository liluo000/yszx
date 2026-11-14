package com.yunshuzhixue.modules.help;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 协议查看（S-HLP-01）：只读接口，游客可访问。
 * 用例会清空并恢复 agreement_version，开发库里该类协议由后台 A-CFG-04 发布，测试不保留插入的数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentAgreementIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearAgreements() {
        jdbc.update("DELETE FROM agreement_version");
    }

    @Test
    void latestPublishedVersionIsReturned() throws Exception {
        jdbc.update("INSERT INTO agreement_version (agreement_type, version_no, content, published_by_sys_user_id,"
                + " published_at) VALUES (?, ?, ?, ?, NOW(3))", "user_agreement", 2, "用户协议正文", 1L);
        jdbc.update("INSERT INTO agreement_version (agreement_type, version_no, content, published_by_sys_user_id,"
                + " published_at) VALUES (?, ?, ?, ?, NOW(3))", "user_agreement", 3, "用户协议正文第三版", 1L);

        mvc.perform(get("/api/v1/student/agreements/user_agreement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreement_type").value("user_agreement"))
                .andExpect(jsonPath("$.data.version_no").value(3))
                .andExpect(jsonPath("$.data.content").value("用户协议正文第三版"))
                .andExpect(jsonPath("$.data.published_at").isNotEmpty());
    }

    @Test
    void unpublishedAgreementReturns404() throws Exception {
        mvc.perform(get("/api/v1/student/agreements/privacy_policy"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("协议内容尚未发布"));
    }

    @Test
    void unknownAgreementTypeReturns404() throws Exception {
        mvc.perform(get("/api/v1/student/agreements/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
