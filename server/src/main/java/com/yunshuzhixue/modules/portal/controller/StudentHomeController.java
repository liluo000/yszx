package com.yunshuzhixue.modules.portal.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.portal.service.StudentHomeService;
import com.yunshuzhixue.modules.portal.service.StudentHomeService.HomeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学员端首页（S-PTL-01）：公开，可选登录；游客只返回路线入口 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentHomeController {

    private final StudentHomeService service;

    public StudentHomeController(StudentHomeService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public ApiResponse<HomeView> home() {
        return ApiResponse.ok(service.load());
    }
}
