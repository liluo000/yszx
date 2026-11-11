package com.yunshuzhixue.modules.sysconfig.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.sysconfig.entity.AgreementVersion;
import com.yunshuzhixue.modules.sysconfig.entity.Faq;
import com.yunshuzhixue.modules.sysconfig.entity.SensitiveWord;
import com.yunshuzhixue.modules.sysconfig.service.ConfigService;
import com.yunshuzhixue.modules.sysconfig.service.VideoPlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 系统配置模块的请求与响应对象（字段与 04.API接口设计.md 一致） */
public final class ConfigDtos {

    private ConfigDtos() {
    }

    public record ConfigView(String configKey, JsonNode configValue, String description, Instant updatedAt) {
        public static ConfigView of(ConfigService.ConfigItem item) {
            return new ConfigView(item.configKey(), item.configValue(), item.description(),
                    UtcTime.toInstant(item.row().getUpdatedAt()));
        }
    }

    public record UpdateConfigRequest(@NotNull(message = "请填写配置值") JsonNode configValue) {
    }

    public record AgreementView(String agreementType, Integer versionNo, String content, Instant publishedAt) {
        public static AgreementView of(String type, AgreementVersion v) {
            return v == null ? new AgreementView(type, null, null, null)
                    : new AgreementView(type, v.getVersionNo(), v.getContent(), UtcTime.toInstant(v.getPublishedAt()));
        }
    }

    public record PublishAgreementRequest(
            @NotBlank(message = "请填写协议内容") @Size(max = 1000000, message = "协议内容最多 100 万字") String content) {
    }

    public record PublishAgreementResponse(int versionNo) {
    }

    public record SensitiveWordView(Long id, String word, Instant createdAt) {
        public static SensitiveWordView of(SensitiveWord w) {
            return new SensitiveWordView(w.getId(), w.getWord(), UtcTime.toInstant(w.getCreatedAt()));
        }
    }

    public record AddSensitiveWordRequest(
            @NotBlank(message = "请填写敏感词") @Size(max = 100, message = "敏感词最多 100 个字符") String word) {
    }

    public record IdResponse(Long id) {
    }

    private static final String DOMAIN = "^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$";

    public record CreatePlatformRequest(
            @NotBlank(message = "请填写平台名称") @Size(max = 50, message = "平台名称最多 50 个字符") String name,
            @NotEmpty(message = "至少填写 1 个域名") @Size(max = 20, message = "最多 20 个域名")
            List<@NotBlank(message = "域名不能为空") @Pattern(regexp = DOMAIN, message = "域名格式不正确") String> domains) {
    }

    /** 传 domains 时整体替换原有域名 */
    public record UpdatePlatformRequest(
            @Size(min = 1, max = 50, message = "平台名称为 1~50 个字符") String name,
            @Size(min = 1, max = 20, message = "域名为 1~20 个")
            List<@NotBlank(message = "域名不能为空") @Pattern(regexp = DOMAIN, message = "域名格式不正确") String> domains) {
    }

    public record PlatformStatusRequest(
            @NotBlank(message = "请选择状态") @Pattern(regexp = "^(enabled|disabled)$", message = "状态不正确") String status) {
    }

    public record PlatformView(Long id, String name, String status, List<String> domains) {
        public static PlatformView of(VideoPlatformService.PlatformItem item) {
            return new PlatformView(item.platform().getId(), item.platform().getName(), item.platform().getStatus(),
                    item.domains());
        }
    }

    public record FaqView(Long id, String question, String answer, Integer sortNo) {
        public static FaqView of(Faq f) {
            return new FaqView(f.getId(), f.getQuestion(), f.getAnswer(), f.getSortNo());
        }
    }

    public record CreateFaqRequest(
            @NotBlank(message = "请填写问题") @Size(max = 200, message = "问题最多 200 个字符") String question,
            @NotBlank(message = "请填写回答") @Size(max = 10000, message = "回答最多 10000 个字符") String answer,
            @NotNull(message = "请填写排序号") Integer sortNo) {
    }

    public record UpdateFaqRequest(
            @Size(min = 1, max = 200, message = "问题为 1~200 个字符") String question,
            @Size(min = 1, max = 10000, message = "回答为 1~10000 个字符") String answer,
            Integer sortNo) {
    }

    public record SortRequest(
            @NotEmpty(message = "请提供排序") @Size(max = 500, message = "最多 500 项") List<@Valid SortEntry> items) {
    }

    public record SortEntry(@NotNull(message = "缺少 id") Long id, @NotNull(message = "缺少排序号") Integer sortNo) {
    }

    public record UploadResponse(String url) {
    }
}
