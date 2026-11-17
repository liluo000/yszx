package com.yunshuzhixue.modules.help.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.help.service.StudentHelpService;
import com.yunshuzhixue.modules.help.service.StudentHelpService.HelpView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 帮助中心（S-HLP-03）：常见问题与客服联系方式，游客可看 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentHelpController {

    private final StudentHelpService help;

    public StudentHelpController(StudentHelpService help) {
        this.help = help;
    }

    @GetMapping("/help")
    public ApiResponse<HelpView> load() {
        return ApiResponse.ok(help.load());
    }
}
