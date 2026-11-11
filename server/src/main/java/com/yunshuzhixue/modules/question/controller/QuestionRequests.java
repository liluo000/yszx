package com.yunshuzhixue.modules.question.controller;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

/** 题库接口共用的参数检查 */
final class QuestionRequests {

    private QuestionRequests() {
    }

    static void checkStatus(String status) {
        if (status != null && !status.isEmpty() && !status.matches("^(on_shelf|off_shelf)$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "状态不正确");
        }
    }

    static void checkXlsx(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请上传 xlsx 格式的导入模板");
        }
    }
}
