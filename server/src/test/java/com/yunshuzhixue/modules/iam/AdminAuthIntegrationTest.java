package com.yunshuzhixue.modules.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 后台登录全流程（A-IAM-01~04）。连接 deploy/dev 中运行的本地 MySQL 和 Redis，测试数据在结束后清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthIntegrationTest {

    private static final String PASSWORD = "Init#2026";

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

    private final List<SysUser> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (SysUser user : created) {
            operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getSysUserId, user.getId()));
            sysUserMapper.deleteById(user.getId());
            redis.delete("iam:admin:login-failures:" + user.getAccount());
        }
        redis.delete("iam:admin:login-failures:it_missing_account");
    }

    @Test
    void loginSucceedsAndRecordsOperationLog() throws Exception {
        SysUser user = createUser("enabled", false);

        mvc.perform(login(user.getAccount(), PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.must_change_password").value(false))
                .andExpect(jsonPath("$.data.user.account").value(user.getAccount()))
                .andExpect(jsonPath("$.data.user.role").value("content_operator"))
                .andExpect(jsonPath("$.data.user.password_hash").doesNotExist());

        List<OperationLog> logs = operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getSysUserId, user.getId()));
        assertThat(logs).extracting(OperationLog::getAction).containsExactly("后台登录");
        assertThat(sysUserMapper.selectById(user.getId()).getLastLoginAt()).isNotNull();
    }

    @Test
    void wrongPasswordCountsDownThenLocksForFifteenMinutes() throws Exception {
        SysUser user = createUser("enabled", false);
        for (int remaining = 4; remaining >= 1; remaining--) {
            mvc.perform(login(user.getAccount(), "Wrong#2026"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.errors[0].remaining_attempts").value(remaining));
        }
        mvc.perform(login(user.getAccount(), "Wrong#2026"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        // 锁定期间即使密码正确也不能登录
        mvc.perform(login(user.getAccount(), PASSWORD))
                .andExpect(status().isLocked());
    }

    @Test
    void unknownAccountGetsTheSameErrorAsWrongPassword() throws Exception {
        mvc.perform(login("it_missing_account", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        SysUser user = createUser("disabled", false);
        mvc.perform(login(user.getAccount(), PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void blankFieldsReturnValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/admin/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems("account", "password")));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingTheConsole() throws Exception {
        SysUser user = createUser("enabled", true);
        String token = loginToken(user.getAccount(), PASSWORD);

        mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.must_change_password").value(true))
                .andExpect(jsonPath("$.data.permissions[0]").value("workbench"));
        mvc.perform(get("/api/v1/admin/workbench").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(changePassword(token, "Wrong#2026", "NewPass#2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OLD_PASSWORD_INCORRECT"));
        mvc.perform(changePassword(token, PASSWORD, "NewPass#2026"))
                .andExpect(status().isOk());

        // 修改后所有设备退出，需要用新密码重新登录
        mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String newToken = loginToken(user.getAccount(), "NewPass#2026");
        mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + newToken))
                .andExpect(jsonPath("$.data.must_change_password").value(false));
        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getSysUserId, user.getId())))
                .extracting(OperationLog::getAction).contains("修改密码");
    }

    @Test
    void logoutInvalidatesTheCurrentToken() throws Exception {
        SysUser user = createUser("enabled", false);
        String token = loginToken(user.getAccount(), PASSWORD);
        mvc.perform(delete("/api/v1/admin/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private SysUser createUser(String status, boolean mustChangePassword) {
        SysUser user = new SysUser();
        user.setAccount("it_" + UUID.randomUUID().toString().substring(0, 8));
        user.setName("测试账号");
        user.setPhone("13900000000");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole("content_operator");
        user.setStatus(status);
        user.setMustChangePassword(mustChangePassword);
        sysUserMapper.insert(user);
        created.add(user);
        return user;
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String account, String password) {
        return post("/api/v1/admin/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder changePassword(String token, String oldPassword,
                                                                                       String newPassword) {
        return put("/api/v1/admin/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"old_password\":\"" + oldPassword + "\",\"new_password\":\"" + newPassword + "\"}");
    }

    private String loginToken(String account, String password) throws Exception {
        MvcResult result = mvc.perform(login(account, password)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }
}
