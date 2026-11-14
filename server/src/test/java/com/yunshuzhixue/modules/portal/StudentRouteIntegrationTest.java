package com.yunshuzhixue.modules.portal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 学员端学习路线（S-PTL-02~04）：列表、详情与搜索 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentRouteIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listReturnsPageStructure() throws Exception {
        mvc.perform(get("/api/v1/student/routes").param("page", "1").param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10))
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void detailReturnsStagesAndCourses() throws Exception {
        MvcResult list = mvc.perform(get("/api/v1/student/routes").param("page_size", "100"))
                .andExpect(status().isOk()).andReturn();
        List<Integer> ids = JsonPath.read(list.getResponse().getContentAsString(), "$.data.items[*].id");
        if (ids.isEmpty()) {
            return; // 库中没有已上架路线时只验证列表结构
        }
        mvc.perform(get("/api/v1/student/routes/" + ids.get(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ids.get(0)))
                .andExpect(jsonPath("$.data.stages").isArray())
                .andExpect(jsonPath("$.data.progress").doesNotExist());
    }

    @Test
    void detailOfMissingRouteReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/student/routes/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void searchByRouteNameAndEmptyKeyword() throws Exception {
        MvcResult list = mvc.perform(get("/api/v1/student/routes").param("page_size", "100"))
                .andExpect(status().isOk()).andReturn();
        List<String> names = JsonPath.read(list.getResponse().getContentAsString(), "$.data.items[*].name");
        if (!names.isEmpty()) {
            String keyword = names.get(0).substring(0, Math.min(2, names.get(0).length()));
            mvc.perform(get("/api/v1/student/search").param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.routes").isArray())
                    .andExpect(jsonPath("$.data.courses").isArray());
        }
        mvc.perform(get("/api/v1/student/search").param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
