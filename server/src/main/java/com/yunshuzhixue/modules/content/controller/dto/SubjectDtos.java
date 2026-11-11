package com.yunshuzhixue.modules.content.controller.dto;

import com.yunshuzhixue.modules.content.service.SubjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 学科管理的请求与响应（A-CNT-01~05） */
public final class SubjectDtos {

    private SubjectDtos() {
    }

    public record SubjectView(Long id, String name, String intro, Integer sortNo, long courseCount) {
        public static SubjectView of(SubjectService.SubjectItem item) {
            return new SubjectView(item.subject().getId(), item.subject().getName(), item.subject().getIntro(),
                    item.subject().getSortNo(), item.courseCount());
        }
    }

    public record CreateSubjectRequest(
            @NotBlank(message = "请填写学科名称") @Size(max = 50, message = "学科名称最多 50 个字符") String name,
            @Size(max = 500, message = "简介最多 500 个字符") String intro,
            @NotNull(message = "请填写排序号") Integer sortNo) {
    }

    public record UpdateSubjectRequest(
            @Size(max = 50, message = "学科名称最多 50 个字符") @Pattern(regexp = "(?s).*\\S.*", message = "学科名称不能为空") String name,
            @Size(max = 500, message = "简介最多 500 个字符") String intro,
            Integer sortNo) {
    }

    public record SortRequest(
            @NotEmpty(message = "请提供排序") @Size(max = 500, message = "最多 500 项") List<@Valid SortEntry> items) {
    }

    public record SortEntry(@NotNull(message = "缺少 id") Long id, @NotNull(message = "缺少排序号") Integer sortNo) {
    }

    public record IdResponse(Long id) {
    }
}
