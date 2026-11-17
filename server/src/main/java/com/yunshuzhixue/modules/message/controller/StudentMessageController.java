package com.yunshuzhixue.modules.message.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.message.service.MessageCenterService;
import com.yunshuzhixue.modules.message.service.MessageCenterService.MessageItem;
import com.yunshuzhixue.modules.message.service.MessageCenterService.UnreadCount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消息中心（S-MSG-01~04）：站内信列表、未读数、标记已读与全部已读 */
@RestController
@RequestMapping("/api/v1/student/messages")
public class StudentMessageController {

    private final MessageCenterService service;

    public StudentMessageController(MessageCenterService service) {
        this.service = service;
    }

    /** S-MSG-01 */
    @GetMapping
    public ApiResponse<PageResult<MessageItem>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "is_read", required = false) Boolean isRead) {
        return ApiResponse.ok(service.list(loggedIn(), PageQuery.of(page, pageSize), isRead));
    }

    /** S-MSG-02 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount() {
        return ApiResponse.ok(service.unreadCount(loggedIn()));
    }

    /** S-MSG-03 */
    @PutMapping("/{studentMessageId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long studentMessageId) {
        service.markRead(loggedIn(), studentMessageId);
        return ApiResponse.ok();
    }

    /** S-MSG-04 */
    @PutMapping("/read-all")
    public ApiResponse<Void> readAll() {
        service.readAll(loggedIn());
        return ApiResponse.ok();
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }
}
