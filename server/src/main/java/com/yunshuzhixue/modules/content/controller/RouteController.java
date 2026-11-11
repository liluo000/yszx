package com.yunshuzhixue.modules.content.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.CreateRouteRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.CreateStageRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.RouteAdminView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.RouteDetailView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.RouteOption;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.RoutePreviewView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.RouteStatusRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.SortRequest;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.StageAdminView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.StageDetailView;
import com.yunshuzhixue.modules.content.controller.dto.RouteDtos.UpdateRouteRequest;
import com.yunshuzhixue.modules.content.entity.LearningRoute;
import com.yunshuzhixue.modules.content.service.RouteService;
import com.yunshuzhixue.modules.content.service.StageService;
import com.yunshuzhixue.modules.iam.api.AdminAccess;
import com.yunshuzhixue.modules.iam.api.AdminPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学习路线（A-CNT-14~21、24、28）：内容运营、超级管理员；路线下拉选项所有后台角色可用 */
@RestController
@RequestMapping("/api/v1/admin")
public class RouteController {

    private final RouteService routeService;
    private final StageService stageService;
    private final AdminAccess access;

    public RouteController(RouteService routeService, StageService stageService, AdminAccess access) {
        this.routeService = routeService;
        this.stageService = stageService;
        this.access = access;
    }

    @GetMapping("/routes")
    public ApiResponse<PageResult<RouteAdminView>> list(@RequestParam(required = false) Integer page,
                                                        @RequestParam(name = "page_size", required = false) Integer pageSize,
                                                        @RequestParam(required = false) String status) {
        access.require(AdminPermission.ROUTE);
        if (status != null && !status.isEmpty() && !status.matches("^(on_shelf|off_shelf)$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "状态不正确");
        }
        PageResult<RouteService.RouteItem> result = routeService.list(PageQuery.of(page, pageSize), status);
        return ApiResponse.ok(new PageResult<>(result.items().stream().map(RouteAdminView::of).toList(),
                result.total(), result.page(), result.pageSize()));
    }

    @GetMapping("/route-options")
    public ApiResponse<Map<String, List<RouteOption>>> options() {
        access.current();
        return ApiResponse.ok(Map.of("items", routeService.options().stream()
                .map((LearningRoute r) -> new RouteOption(r.getId(), r.getName(), r.getStatus())).toList()));
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RouteAdminView> create(@Valid @RequestBody CreateRouteRequest request) {
        return ApiResponse.ok(RouteAdminView.of(routeService.create(access.require(AdminPermission.ROUTE),
                request.name(), request.intro(), request.sortNo())));
    }

    @GetMapping("/routes/{id}")
    public ApiResponse<RouteDetailView> detail(@PathVariable Long id) {
        access.require(AdminPermission.ROUTE);
        RouteAdminView route = RouteAdminView.of(routeService.get(id));
        return ApiResponse.ok(new RouteDetailView(route,
                stageService.stagesOf(id).stream().map(StageAdminView::of).toList()));
    }

    @PatchMapping("/routes/{id}")
    public ApiResponse<RouteAdminView> update(@PathVariable Long id, @Valid @RequestBody UpdateRouteRequest request) {
        return ApiResponse.ok(RouteAdminView.of(routeService.update(access.require(AdminPermission.ROUTE), id,
                request.name(), request.intro(), request.sortNo())));
    }

    @PutMapping("/routes/{id}/status")
    public ApiResponse<RouteAdminView> changeStatus(@PathVariable Long id, @Valid @RequestBody RouteStatusRequest request) {
        return ApiResponse.ok(RouteAdminView.of(routeService.changeStatus(access.require(AdminPermission.ROUTE), id,
                request.status())));
    }

    @DeleteMapping("/routes/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        routeService.delete(access.require(AdminPermission.ROUTE), id);
        return ApiResponse.ok();
    }

    /** 预览前台效果，不要求路线已上架；只显示已上架课程 */
    @GetMapping("/routes/{id}/preview")
    public ApiResponse<RoutePreviewView> preview(@PathVariable Long id) {
        access.require(AdminPermission.ROUTE);
        LearningRoute route = routeService.get(id).route();
        return ApiResponse.ok(new RoutePreviewView(route.getId(), route.getName(), route.getIntro(),
                stageService.stagesOf(id).stream().map(StageDetailView::of).toList()));
    }

    @PostMapping("/routes/{id}/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StageAdminView> createStage(@PathVariable Long id, @Valid @RequestBody CreateStageRequest request) {
        return ApiResponse.ok(StageAdminView.of(stageService.create(access.require(AdminPermission.ROUTE), id,
                request.name(), request.intro(), request.sortNo())));
    }

    @PutMapping("/routes/{id}/stages/sort-order")
    public ApiResponse<Void> sortStages(@PathVariable Long id, @Valid @RequestBody SortRequest request) {
        stageService.sortStages(access.require(AdminPermission.ROUTE), id, request.toItems());
        return ApiResponse.ok();
    }
}
