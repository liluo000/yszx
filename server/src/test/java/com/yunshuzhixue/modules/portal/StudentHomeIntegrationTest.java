package com.yunshuzhixue.modules.portal;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 学员端首页（S-PTL-01）：游客返回路线入口，个性化字段为空或 0 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentHomeIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void guestGetsPublishedRoutesWithoutPersonalizedData() throws Exception {
        mvc.perform(get("/api/v1/student/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_guest").value(true))
                .andExpect(jsonPath("$.data.continue_learning").value(nullValue()))
                .andExpect(jsonPath("$.data.my_routes").isEmpty())
                .andExpect(jsonPath("$.data.pending_wrong_question_count").value(0))
                .andExpect(jsonPath("$.data.interview_remaining").value(0))
                .andExpect(jsonPath("$.data.routes").isArray());
    }
}
