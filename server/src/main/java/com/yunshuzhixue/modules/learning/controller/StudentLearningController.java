package com.yunshuzhixue.modules.learning.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.learning.service.LearningRecordService;
import com.yunshuzhixue.modules.learning.service.LearningRecordService.RouteProgressItem;
import com.yunshuzhixue.modules.learning.service.LearningRecordService.TrackItem;
import com.yunshuzhixue.modules.learning.service.LearningService;
import com.yunshuzhixue.modules.learning.service.LearningService.LearnedResult;
import com.yunshuzhixue.modules.learning.service.LearningService.OpenResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学习进度（S-LRN-01~03）：打开课程、标记与取消已学 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentLearningController {

    private final LearningService service;
    private final LearningRecordService records;

    public StudentLearningController(LearningService service, LearningRecordService records) {
        this.service = service;
        this.records = records;
    }

    @PostMapping("/courses/{id}/opens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OpenResult> open(@PathVariable Long id,
                                       @RequestBody(required = false) OpenRequest request) {
        long studentId = loggedIn();
        Long routeId = request == null ? null : request.learningRouteId();
        Long stageId = request == null ? null : request.routeStageId();
        return ApiResponse.ok(service.open(studentId, id, routeId, stageId));
    }

    @PutMapping("/courses/{id}/learned")
    public ApiResponse<LearnedResult> markLearned(@PathVariable Long id) {
        long studentId = loggedIn();
        return ApiResponse.ok(service.markLearned(studentId, id));
    }

    @DeleteMapping("/courses/{id}/learned")
    public ApiResponse<Void> cancelLearned(@PathVariable Long id) {
        long studentId = loggedIn();
        service.cancelLearned(studentId, id);
        return ApiResponse.ok();
    }

    /** S-LRN-04：学习轨迹（打开 / 已学 / 已掌握） */
    @GetMapping("/learning-records/tracks")
    public ApiResponse<PageResult<TrackItem>> tracks(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, name = "page_size") Integer pageSize,
            @RequestParam(name = "record_type", required = false) String recordType,
            @RequestParam(required = false, name = "learning_route_id") Long learningRouteId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        long studentId = loggedIn();
        return ApiResponse.ok(records.tracks(studentId, PageQuery.of(page, pageSize), recordType,
                learningRouteId, startDate, endDate));
    }

    /** S-LRN-05：各路线学习进度 */
    @GetMapping("/learning-records/route-progress")
    public ApiResponse<RouteProgressResult> routeProgress(
            @RequestParam(required = false, name = "learning_route_id") Long learningRouteId) {
        loggedIn();
        return ApiResponse.ok(new RouteProgressResult(records.routeProgress(learningRouteId)));
    }

    private static long loggedIn() {
        StudentStp.LOGIC.checkLogin();
        return StudentStp.LOGIC.getLoginIdAsLong();
    }

    public record OpenRequest(Long learningRouteId, Long routeStageId) {
    }

    /** S-LRN-05 响应：data 是含 items 的对象，不是裸数组 */
    public record RouteProgressResult(List<RouteProgressItem> items) {
    }
}
