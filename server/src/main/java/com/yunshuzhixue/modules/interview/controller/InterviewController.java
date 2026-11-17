package com.yunshuzhixue.modules.interview.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.interview.service.InterviewService;
import com.yunshuzhixue.modules.interview.service.InterviewService.DeviceClaim;
import com.yunshuzhixue.modules.interview.service.InterviewService.InProgressView;
import com.yunshuzhixue.modules.interview.service.InterviewService.InterviewView;
import com.yunshuzhixue.modules.interview.service.InterviewService.Options;
import com.yunshuzhixue.modules.interview.service.InterviewService.ReplyResult;
import com.yunshuzhixue.modules.interview.service.InterviewService.Segment;
import com.yunshuzhixue.modules.interview.service.InterviewService.StartRequest;
import com.yunshuzhixue.modules.interview.service.InterviewService.Termination;
import com.yunshuzhixue.modules.safety.api.SensitiveTextFilter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/** AI 面试（S-IVW-01~07）：S-IVW-06 返回 SSE 流，事件见 04.API接口设计.md 2.8 */
@RestController
@RequestMapping("/api/v1/student/interviews")
public class InterviewController {

    private final InterviewService service;
    private final JsonMapper json;

    public InterviewController(InterviewService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    /** S-IVW-01 获取发起面试的选项 */
    @GetMapping("/options")
    public ApiResponse<Options> options(@RequestParam(name = "learning_route_id") long learningRouteId) {
        return ApiResponse.ok(service.options(loggedIn(), learningRouteId));
    }

    /** S-IVW-02 发起文字面试 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewView> start(@RequestBody StartRequest request) {
        return ApiResponse.ok(service.start(loggedIn(), request));
    }

    /** S-IVW-03 查询进行中的面试 */
    @GetMapping("/in-progress")
    public ApiResponse<InProgressView> inProgress() {
        return ApiResponse.ok(service.inProgress(loggedIn()));
    }

    /** S-IVW-04 获取面试进度（流式中断后补全消息） */
    @GetMapping("/{interviewSessionId}")
    public ApiResponse<InterviewView> view(@PathVariable long interviewSessionId) {
        return ApiResponse.ok(service.view(loggedIn(), interviewSessionId));
    }

    /** S-IVW-05 在当前设备接管文字面试 */
    @PostMapping("/{interviewSessionId}/device-claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceClaim> claimDevice(@PathVariable long interviewSessionId) {
        return ApiResponse.ok(service.claim(loggedIn(), interviewSessionId));
    }

    /** S-IVW-06 提交回答并流式接收 AI 回复 */
    @PostMapping(value = "/{interviewSessionId}/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@PathVariable long interviewSessionId,
                             @RequestBody AnswerRequest request,
                             @RequestHeader(name = "X-Device-Claim", required = false) String claim) {
        long studentId = loggedIn();
        // 全部校验与写库在发送第一帧之前完成，失败仍由全局异常处理器返回统一 JSON
        ReplyResult result = service.submit(studentId, interviewSessionId,
                request == null ? null : request.content(), claim, SensitiveTextFilter.SCENE_TEXT_INTERVIEW);
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            if ("ai_failure".equals(result.end_reason())) {
                send(emitter, "ai_recovering", Map.of());
            }
            for (Segment segment : result.segments()) {
                send(emitter, "message_start", Map.of("message_type", segment.message_type()));
                for (String sentence : segment.sentences()) {
                    send(emitter, "sentence", Map.of("content", sentence));
                }
                Map<String, Object> end = new LinkedHashMap<>();
                end.put("seq_no", segment.last_message_seq_no());
                end.put("current_seq_no", segment.current_seq_no());
                send(emitter, "message_end", end);
            }
            if (result.ended()) {
                send(emitter, "interview_ended", Map.of("end_reason", result.end_reason()));
            }
            emitter.complete();
        } catch (IOException | RuntimeException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** S-IVW-07 主动结束面试 */
    @PostMapping("/{interviewSessionId}/termination")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Termination> terminate(@PathVariable long interviewSessionId,
                                              @RequestHeader(name = "X-Device-Claim", required = false) String claim) {
        return ApiResponse.ok(service.terminate(loggedIn(), interviewSessionId, claim));
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data)));
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }

    public record AnswerRequest(String content) {
    }
}
