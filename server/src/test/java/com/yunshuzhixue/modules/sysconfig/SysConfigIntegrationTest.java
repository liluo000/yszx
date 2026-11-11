package com.yunshuzhixue.modules.sysconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.infra.storage.ObjectStorage;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.modules.sysconfig.entity.AgreementVersion;
import com.yunshuzhixue.modules.sysconfig.entity.Faq;
import com.yunshuzhixue.modules.sysconfig.entity.SensitiveWord;
import com.yunshuzhixue.modules.sysconfig.entity.SysConfig;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatformDomain;
import com.yunshuzhixue.modules.sysconfig.mapper.AgreementVersionMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.FaqMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.SensitiveWordMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.SysConfigMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformDomainMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 系统配置（A-CFG-01~18）。连接本地 MySQL、Redis、SeaweedFS；改过的配置和新增的数据在结束后恢复或删除 */
@SpringBootTest
@AutoConfigureMockMvc
class SysConfigIntegrationTest {

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
    @Autowired
    private SysConfigMapper configMapper;
    @Autowired
    private AgreementVersionMapper agreementMapper;
    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;
    @Autowired
    private VideoPlatformMapper platformMapper;
    @Autowired
    private VideoPlatformDomainMapper domainMapper;
    @Autowired
    private FaqMapper faqMapper;
    @Autowired
    private ObjectStorage objectStorage;

    private AdminTestAccounts accounts;
    private String token;
    private final List<SysConfig> originalConfigs = new ArrayList<>();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        token = accounts.login(accounts.create("super_admin"));
    }

    @AfterEach
    void cleanUp() {
        // updateById 会跳过空字段，这里显式恢复值和修改人（初始数据的修改人为空）
        for (SysConfig original : originalConfigs) {
            configMapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                    .eq(SysConfig::getId, original.getId())
                    .set(SysConfig::getConfigValue, original.getConfigValue())
                    .set(SysConfig::getUpdatedBySysUserId, original.getUpdatedBySysUserId()));
        }
        agreementMapper.delete(new LambdaQueryWrapper<AgreementVersion>().like(AgreementVersion::getContent, tag));
        sensitiveWordMapper.delete(new LambdaQueryWrapper<SensitiveWord>().like(SensitiveWord::getWord, tag));
        faqMapper.delete(new LambdaQueryWrapper<Faq>().like(Faq::getQuestion, tag));
        for (VideoPlatform platform : platformMapper.selectList(
                new LambdaQueryWrapper<VideoPlatform>().like(VideoPlatform::getName, tag))) {
            domainMapper.delete(new LambdaQueryWrapper<VideoPlatformDomain>()
                    .eq(VideoPlatformDomain::getVideoPlatformId, platform.getId()));
            platformMapper.deleteById(platform.getId());
        }
        accounts.cleanUp();
    }

    @Test
    void defaultConfigsAreSeededAndValidatedOnUpdate() throws Exception {
        mvc.perform(get("/api/v1/admin/configs").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(17))
                .andExpect(jsonPath("$.data.items[?(@.config_key=='exam.paper_rule')].config_value.short_answer.score")
                        .value(10));

        remember("ai.interview_daily_limit");
        mvc.perform(put("/api/v1/admin/configs/ai.interview_daily_limit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config_value\":5}"))
                .andExpect(status().isOk());
        assertThat(configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "ai.interview_daily_limit")).getConfigValue()).isEqualTo("5");
        mvc.perform(put("/api/v1/admin/configs/ai.interview_daily_limit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config_value\":\"5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config_value"));

        remember("interview.grade_thresholds");
        mvc.perform(put("/api/v1/admin/configs/interview.grade_thresholds").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config_value\":{\"excellent\":60,\"good\":70}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/configs/unknown.key").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"config_value\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishingAnAgreementCreatesTheNextVersion() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/admin/agreements/privacy_policy/versions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"隐私政策 " + tag + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        int version = JsonPath.read(first.getResponse().getContentAsString(), "$.data.version_no");
        mvc.perform(post("/api/v1/admin/agreements/privacy_policy/versions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"隐私政策修订 " + tag + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version_no").value(version + 1));
        mvc.perform(get("/api/v1/admin/agreements").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.agreement_type=='privacy_policy')].content")
                        .value("隐私政策修订 " + tag));
    }

    @Test
    void sensitiveWordsCanBeAddedImportedAndDeleted() throws Exception {
        MvcResult added = mvc.perform(post("/api/v1/admin/sensitive-words").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"词" + tag + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/api/v1/admin/sensitive-words").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"词" + tag.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SENSITIVE_WORD_EXISTS"));

        byte[] xlsx = workbook("敏感词", "甲" + tag, "乙" + tag, "甲" + tag, "词" + tag);
        mvc.perform(multipart("/api/v1/admin/sensitive-words/imports")
                        .file(new MockMultipartFile("file", "words.xlsx", null, xlsx)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2))
                .andExpect(jsonPath("$.data.failure_count").value(2))
                .andExpect(jsonPath("$.data.failures[0].row_no").value(4))
                .andExpect(jsonPath("$.data.failures[0].reason").value("与第 2 行重复"))
                .andExpect(jsonPath("$.data.failures[1].reason").value("敏感词已存在"));

        mvc.perform(get("/api/v1/admin/sensitive-words").header("Authorization", token).param("keyword", tag))
                .andExpect(jsonPath("$.data.total").value(3));

        Long id = ((Number) JsonPath.read(added.getResponse().getContentAsString(), "$.data.id")).longValue();
        mvc.perform(delete("/api/v1/admin/sensitive-words/" + id).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/sensitive-words").header("Authorization", token).param("keyword", tag))
                .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/v1/admin/import-templates/sensitive_word").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void videoPlatformDomainsMustBeUnique() throws Exception {
        mvc.perform(get("/api/v1/admin/video-platforms").header("Authorization", token))
                .andExpect(jsonPath("$.data.items[?(@.name=='B 站')].domains[0]").value("bilibili.com"));

        MvcResult created = mvc.perform(post("/api/v1/admin/video-platforms").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"平台" + tag + "\",\"domains\":[\"V" + tag + ".example.com\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();
        mvc.perform(get("/api/v1/admin/video-platforms").header("Authorization", token))
                .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].domains[0]").value("v" + tag + ".example.com"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + id + ")].status").value("enabled"));

        mvc.perform(patch("/api/v1/admin/video-platforms/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"domains\":[\"b23.tv\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_DOMAIN_EXISTS"));
        mvc.perform(patch("/api/v1/admin/video-platforms/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"domains\":[\"w" + tag + ".example.com\"]}"))
                .andExpect(status().isOk());
        assertThat(domainMapper.selectList(new LambdaQueryWrapper<VideoPlatformDomain>()
                .eq(VideoPlatformDomain::getVideoPlatformId, id))).extracting(VideoPlatformDomain::getDomain)
                .containsExactly("w" + tag + ".example.com");
        mvc.perform(put("/api/v1/admin/video-platforms/" + id + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk());
        assertThat(platformMapper.selectById(id).getStatus()).isEqualTo("disabled");
    }

    @Test
    void faqsCanBeCreatedSortedAndDeleted() throws Exception {
        Long first = createFaq("问题一" + tag, 10);
        Long second = createFaq("问题二" + tag, 20);
        mvc.perform(put("/api/v1/admin/faqs/sort-order").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":" + first + ",\"sort_no\":30},{\"id\":" + second + ",\"sort_no\":5}]}"))
                .andExpect(status().isOk());
        assertThat(faqMapper.selectById(first).getSortNo()).isEqualTo(30);
        assertThat(faqMapper.selectById(second).getSortNo()).isEqualTo(5);

        mvc.perform(delete("/api/v1/admin/faqs/" + first).header("Authorization", token)).andExpect(status().isOk());
        assertThat(faqMapper.selectById(first)).isNull();
    }

    @Test
    void uploadedImageIsPubliclyReadable() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MvcResult result = mvc.perform(multipart("/api/v1/admin/uploads")
                        .file(new MockMultipartFile("file", "qr.png", "image/png", png)).header("Authorization", token))
                .andExpect(status().isCreated()).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.data.url");
        assertThat(url).contains("/public/images/").endsWith(".png");

        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(png);
        objectStorage.delete(url.substring(url.indexOf("public/")));

        mvc.perform(multipart("/api/v1/admin/uploads")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID"));
    }

    private Long createFaq(String question, int sortNo) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/faqs").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"回答\",\"sort_no\":" + sortNo + "}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void remember(String key) {
        originalConfigs.add(configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)));
    }

    private static byte[] workbook(String header, String... words) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue(header);
            for (int i = 0; i < words.length; i++) {
                sheet.createRow(i + 1).createCell(0).setCellValue(words[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
