package com.yunshuzhixue.modules.portal.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.portal.service.StudentRouteService;
import com.yunshuzhixue.modules.portal.service.StudentRouteService.RouteCardView;
import com.yunshuzhixue.modules.portal.service.StudentRouteService.RouteDetailView;
import com.yunshuzhixue.modules.portal.service.StudentRouteService.SearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学员端学习路线（S-PTL-02~04）：公开，可选登录 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentRouteController {

    private static final int KEYWORD_MAX = 50;

    private final StudentRouteService service;

    public StudentRouteController(StudentRouteService service) {
        this.service = service;
    }

    @GetMapping("/routes")
    public ApiResponse<PageResult<RouteCardView>> routes(@RequestParam(required = false) Integer page,
                                                          @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(service.routes(PageQuery.of(page, pageSize)));
    }

    @GetMapping("/routes/{id}")
    public ApiResponse<RouteDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/search")
    public ApiResponse<SearchResult> search(@RequestParam(required = false) String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || kw.length() > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请输入 1~" + KEYWORD_MAX + " 个字符的关键词");
        }
        return ApiResponse.ok(service.search(kw));
    }
}
