package com.yunshuzhixue.common.api;

import java.util.List;

/** 分页结构：{items, total, page, page_size}，页码从 1 开始（04.API接口设计.md 2.3） */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
}
