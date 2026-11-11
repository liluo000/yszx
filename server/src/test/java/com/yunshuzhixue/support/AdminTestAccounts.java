package com.yunshuzhixue.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 集成测试用的临时后台账号：账号以 it_ 开头，测试结束后连同其操作日志一起删除 */
public class AdminTestAccounts {

    public static final String PASSWORD = "Init#2026";

    private final MockMvc mvc;
    private final SysUserMapper sysUserMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final List<Long> created = new ArrayList<>();

    public AdminTestAccounts(MockMvc mvc, SysUserMapper sysUserMapper, OperationLogMapper operationLogMapper,
                             PasswordEncoder passwordEncoder, StringRedisTemplate redis) {
        this.mvc = mvc;
        this.sysUserMapper = sysUserMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    public SysUser create(String role) {
        SysUser user = new SysUser();
        user.setAccount("it_" + UUID.randomUUID().toString().substring(0, 8));
        user.setName("测试账号");
        user.setPhone("13900000000");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus("enabled");
        user.setMustChangePassword(false);
        sysUserMapper.insert(user);
        created.add(user.getId());
        return user;
    }

    /** 由接口新建的账号也登记进来，以便清理 */
    public void track(Long id) {
        created.add(id);
    }

    public String login(SysUser user) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + user.getAccount() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    public void cleanUp() {
        for (Long id : created) {
            SysUser user = sysUserMapper.selectById(id);
            operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getSysUserId, id));
            if (user != null) {
                redis.delete("iam:admin:login-failures:" + user.getAccount());
                sysUserMapper.deleteById(id);
            }
        }
        created.clear();
    }
}
