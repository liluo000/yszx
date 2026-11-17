package com.yunshuzhixue.modules.interview.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.interview.service.WsTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** S-IVW-08：获取 WebSocket 一次性票据 */
@RestController
@RequestMapping("/api/v1/student")
public class WsTicketController {

    private final WsTicketService tickets;

    public WsTicketController(WsTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/ws-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WsTicketService.Ticket> issue(@RequestBody TicketRequest request) {
        StudentStp.LOGIC.checkLogin();
        String purpose = request == null ? null : request.purpose();
        if (!"voice_interview".equals(purpose) && !"voice_input".equals(purpose)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "票据用途不正确");
        }
        return ApiResponse.ok(tickets.issue(StudentStp.LOGIC.getLoginIdAsLong(), purpose));
    }

    public record TicketRequest(String purpose) {
    }
}
