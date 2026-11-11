package com.yunshuzhixue.modules.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 系统用户、角色、操作日志（A-IAM-05~10、A-AUD-01）。连接本地 MySQL 和 Redis，测试数据在结束后清理 */
@SpringBootTest
@AutoConfigureMockMvc
class SysUserIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;

    private AdminTestAccounts accounts;
    private SysUser superAdmin;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        superAdmin = accounts.create("super_admin");
        token = accounts.login(superAdmin);
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp();
    }

    @Test
    void createdAccountGetsTemporaryPasswordThatMustBeChanged() throws Exception {
        String account = "it_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mvc.perform(post("/api/v1/admin/sys-users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"name\":\"新运营\",\"phone\":\"13900000001\","
                                + "\"role\":\"student_operator\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.account").value(account))
                .andExpect(jsonPath("$.data.temporary_password").isNotEmpty())
                .andReturn();
        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id")).longValue();
        accounts.track(id);
        String temporary = JsonPath.read(result.getResponse().getContentAsString(), "$.data.temporary_password");
        SysUser saved = sysUserMapper.selectById(id);
        assertThat(passwordEncoder.matches(temporary, saved.getPasswordHash())).isTrue();
        assertThat(saved.getMustChangePassword()).isTrue();

        mvc.perform(post("/api/v1/admin/sys-users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account.toUpperCase() + "\",\"name\":\"重复\",\"phone\":\"13900000001\","
                                + "\"role\":\"student_operator\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        assertThat(actionsOf(superAdmin)).contains("新建账号");
    }

    @Test
    void resetPasswordIssuesNewTemporaryPasswordAndSignsTheUserOut() throws Exception {
        SysUser operator = accounts.create("content_operator");
        String operatorToken = accounts.login(operator);

        MvcResult result = mvc.perform(post("/api/v1/admin/sys-users/" + operator.getId() + "/password-resets")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();
        String temporary = JsonPath.read(result.getResponse().getContentAsString(), "$.data.temporary_password");
        SysUser saved = sysUserMapper.selectById(operator.getId());
        assertThat(passwordEncoder.matches(temporary, saved.getPasswordHash())).isTrue();
        assertThat(saved.getMustChangePassword()).isTrue();

        mvc.perform(get("/api/v1/admin/me").header("Authorization", operatorToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotDisableOwnAccount() throws Exception {
        mvc.perform(put("/api/v1/admin/sys-users/" + superAdmin.getId() + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"disabled\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void disablingAnAccountSignsItOut() throws Exception {
        SysUser operator = accounts.create("content_operator");
        String operatorToken = accounts.login(operator);

        mvc.perform(put("/api/v1/admin/sys-users/" + operator.getId() + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("disabled"));
        mvc.perform(get("/api/v1/admin/me").header("Authorization", operatorToken))
                .andExpect(status().isUnauthorized());
        assertThat(actionsOf(superAdmin)).contains("停用账号");
    }

    @Test
    void editChangesNameAndRole() throws Exception {
        SysUser operator = accounts.create("content_operator");
        mvc.perform(patch("/api/v1/admin/sys-users/" + operator.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"role\":\"student_operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名"))
                .andExpect(jsonPath("$.data.role").value("student_operator"));
    }

    @Test
    void contentOperatorCannotManageSystem() throws Exception {
        SysUser operator = accounts.create("content_operator");
        String operatorToken = accounts.login(operator);

        for (String path : List.of("/api/v1/admin/sys-users", "/api/v1/admin/roles", "/api/v1/admin/operation-logs",
                "/api/v1/admin/configs", "/api/v1/admin/faqs", "/api/v1/admin/sensitive-words")) {
            mvc.perform(get(path).header("Authorization", operatorToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        // 内容运营需要在课程表单中选择视频平台
        mvc.perform(get("/api/v1/admin/video-platforms").header("Authorization", operatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void rolesAndOperationLogsAreListed() throws Exception {
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].role").value("super_admin"));

        mvc.perform(get("/api/v1/admin/operation-logs").header("Authorization", token)
                        .param("sys_user_id", superAdmin.getId().toString()).param("module", "账号"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("后台登录"))
                .andExpect(jsonPath("$.data.page_size").value(20));
    }

    @Test
    void operationLogsCanBeExportedWithFiltersAndTheExportIsLogged() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/operation-log-exports").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sys_user_id\":" + superAdmin.getId() + ",\"module\":\"账号\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("操作类型");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("后台登录");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("超级管理员");
        }
        assertThat(actionsOf(superAdmin)).contains("导出");

        mvc.perform(post("/api/v1/admin/operation-log-exports").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start_date\":\"2026-02-01\",\"end_date\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        SysUser operator = accounts.create("content_operator");
        mvc.perform(post("/api/v1/admin/operation-log-exports").header("Authorization", accounts.login(operator))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private List<String> actionsOf(SysUser user) {
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, user.getId()))
                .stream().map(OperationLog::getAction).toList();
    }
}
