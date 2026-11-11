package com.yunshuzhixue.modules.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatform;
import com.yunshuzhixue.modules.sysconfig.entity.VideoPlatformDomain;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformDomainMapper;
import com.yunshuzhixue.modules.sysconfig.mapper.VideoPlatformMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 视频平台（需求 7.8，A-CFG-09~12）。停用后不能用于新建课程，已有课程不受影响；平台不删除 */
@Service
public class VideoPlatformService {

    public record PlatformItem(VideoPlatform platform, List<String> domains) {
    }

    private final VideoPlatformMapper platformMapper;
    private final VideoPlatformDomainMapper domainMapper;
    private final ConfigAudit audit;

    public VideoPlatformService(VideoPlatformMapper platformMapper, VideoPlatformDomainMapper domainMapper,
                                ConfigAudit audit) {
        this.platformMapper = platformMapper;
        this.domainMapper = domainMapper;
        this.audit = audit;
    }

    public List<PlatformItem> list() {
        Map<Long, List<String>> domains = domainMapper.selectList(
                        new LambdaQueryWrapper<VideoPlatformDomain>().orderByAsc(VideoPlatformDomain::getId))
                .stream().collect(Collectors.groupingBy(VideoPlatformDomain::getVideoPlatformId,
                        Collectors.mapping(VideoPlatformDomain::getDomain, Collectors.toList())));
        return platformMapper.selectList(new LambdaQueryWrapper<VideoPlatform>().orderByAsc(VideoPlatform::getId))
                .stream().map(p -> new PlatformItem(p, domains.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Transactional
    public PlatformItem create(CurrentAdmin operator, String name, List<String> domains) {
        List<String> normalized = normalize(domains);
        ensureNameFree(name.trim(), null);
        ensureDomainsFree(normalized, null);
        VideoPlatform platform = new VideoPlatform();
        platform.setName(name.trim());
        platform.setStatus("enabled");
        platformMapper.insert(platform);
        saveDomains(platform.getId(), normalized);
        audit.record(operator, "新增视频平台", "video_platform", platform.getId(), platform.getName());
        return get(platform.getId());
    }

    @Transactional
    public PlatformItem update(CurrentAdmin operator, Long id, String name, List<String> domains) {
        VideoPlatform platform = require(id);
        if (name != null) {
            ensureNameFree(name.trim(), id);
            VideoPlatform update = new VideoPlatform();
            update.setId(id);
            update.setName(name.trim());
            platformMapper.updateById(update);
        }
        if (domains != null) {
            List<String> normalized = normalize(domains);
            ensureDomainsFree(normalized, id);
            domainMapper.delete(new LambdaQueryWrapper<VideoPlatformDomain>().eq(VideoPlatformDomain::getVideoPlatformId, id));
            saveDomains(id, normalized);
        }
        audit.record(operator, "编辑视频平台", "video_platform", id, name == null ? platform.getName() : name.trim());
        return get(id);
    }

    @Transactional
    public PlatformItem changeStatus(CurrentAdmin operator, Long id, String status) {
        VideoPlatform platform = require(id);
        VideoPlatform update = new VideoPlatform();
        update.setId(id);
        update.setStatus(status);
        platformMapper.updateById(update);
        audit.record(operator, "enabled".equals(status) ? "启用视频平台" : "停用视频平台", "video_platform", id,
                platform.getName());
        return get(id);
    }

    private PlatformItem get(Long id) {
        return list().stream().filter(p -> p.platform().getId().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private VideoPlatform require(Long id) {
        VideoPlatform platform = platformMapper.selectById(id);
        if (platform == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return platform;
    }

    private static List<String> normalize(List<String> domains) {
        List<String> normalized = domains.stream().map(d -> d.trim().toLowerCase()).toList();
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "域名不能重复");
        }
        return normalized;
    }

    private void ensureNameFree(String name, Long selfId) {
        VideoPlatform existing = platformMapper.selectOne(
                new LambdaQueryWrapper<VideoPlatform>().eq(VideoPlatform::getName, name));
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw new BusinessException(ErrorCode.PLATFORM_NAME_EXISTS);
        }
    }

    private void ensureDomainsFree(List<String> domains, Long selfId) {
        List<VideoPlatformDomain> used = domainMapper.selectList(
                new LambdaQueryWrapper<VideoPlatformDomain>().in(VideoPlatformDomain::getDomain, domains));
        List<String> taken = used.stream().filter(d -> !Objects.equals(d.getVideoPlatformId(), selfId))
                .map(VideoPlatformDomain::getDomain).toList();
        if (!taken.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_DOMAIN_EXISTS, "域名已被其他平台使用：" + String.join("、", taken));
        }
    }

    private void saveDomains(Long platformId, List<String> domains) {
        for (String domain : domains) {
            VideoPlatformDomain row = new VideoPlatformDomain();
            row.setVideoPlatformId(platformId);
            row.setDomain(domain);
            domainMapper.insert(row);
        }
    }
}
