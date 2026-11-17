package com.yunshuzhixue.modules.portal.service;

import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.CourseItem;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteDetail;
import com.yunshuzhixue.modules.content.api.RouteDirectory.StageDetail;
import com.yunshuzhixue.modules.learning.api.LearningPositionApi;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.wrongbook.api.WrongQuestionCounter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 学员端首页（需求 5.2，S-PTL-01）。
 *
 * <p>继续学习接 M08 的上次学习位置，我的路线进度按 M08 的课程状态汇总，
 * 待重做错题接 M10、今日面试剩余次数接 M13。</p>
 */
@Service
public class StudentHomeService {

    /** 游客与已登录学员共用；个性化字段在游客时为空 */
    public record HomeView(boolean isGuest, ContinueLearningView continueLearning, List<MyRoute> myRoutes,
                           long pendingWrongQuestionCount, long interviewRemaining, List<RouteCard> routes) {
    }

    /** 我的路线进度（需求 5.2）：学过的路线及其已学、已掌握进度 */
    public record MyRoute(Long id, String name, String intro, List<RouteDirectory.StageBrief> stages,
                          RouteProgress progress) {
    }

    /** 上次学习的路线、阶段与课程，含该路线进度与本阶段课程清单（需求 5.2、5.3.2） */
    public record ContinueLearningView(Long learningRouteId, String routeName, Long routeStageId, String stageName,
                                       Long courseId, String courseTitle, boolean isLearned, boolean isMastered,
                                       RouteProgress progress, List<StageCourse> stageCourses) {
    }

    public record RouteProgress(int learnedCourseCount, int totalCourseCount,
                                int masteredCourseCount, int configuredCourseCount) {
    }

    public record StageCourse(Long id, String title, boolean isLearned, boolean isMastered) {
    }

    private final RouteDirectory directory;
    private final WrongQuestionCounter wrongQuestions;
    private final AiQuotaApi quota;
    private final LearningPositionApi positions;

    public StudentHomeService(RouteDirectory directory, WrongQuestionCounter wrongQuestions, AiQuotaApi quota,
                              LearningPositionApi positions) {
        this.directory = directory;
        this.wrongQuestions = wrongQuestions;
        this.quota = quota;
        this.positions = positions;
    }

    public HomeView load() {
        boolean guest = !StudentStp.LOGIC.isLogin();
        List<RouteCard> published = directory.publishedRoutes();
        long pendingWrong = 0;
        long interviewRemaining = 0;
        ContinueLearningView continueLearning = null;
        List<MyRoute> myRoutes = List.of();
        if (!guest) {
            long studentId = StudentStp.LOGIC.getLoginIdAsLong();
            pendingWrong = wrongQuestions.countRedoable(studentId);
            interviewRemaining = quota.current(studentId).interviewRemaining();
            continueLearning = continueLearning(studentId);
            myRoutes = myRoutes(continueLearning == null ? null : continueLearning.learningRouteId());
        }
        return new HomeView(guest, continueLearning, myRoutes, pendingWrong, interviewRemaining, published);
    }

    /**
     * 我的路线进度：只列有已学课程的路线；上次学习的路线即使还没标记已学也列入，
     * 避免刚点开课程时该区块一片空白。
     */
    private List<MyRoute> myRoutes(Long continueRouteId) {
        List<MyRoute> result = new ArrayList<>();
        for (RouteCard card : directory.publishedRoutes()) {
            RouteDetail detail = directory.routeDetail(card.id());
            if (detail == null) {
                continue;
            }
            List<CourseItem> all = detail.stages().stream().flatMap(stage -> stage.courses().stream()).toList();
            int learned = (int) all.stream().filter(CourseItem::isLearned).count();
            boolean current = card.id().equals(continueRouteId);
            if (learned == 0 && !current) {
                continue;
            }
            int mastered = (int) all.stream().filter(CourseItem::isMastered).count();
            int configured = (int) all.stream().filter(course -> course.practiceQuestionCount() > 0).count();
            result.add(new MyRoute(card.id(), card.name(), card.intro(), card.stages(),
                    new RouteProgress(learned, all.size(), mastered, configured)));
        }
        return result;
    }


    /** 路线已下架、或上次课程已不在当前编排里时不显示继续学习 */
    private ContinueLearningView continueLearning(long studentId) {
        LearningPositionApi.Position position = positions.current(studentId);
        if (position == null) {
            return null;
        }
        RouteDetail detail = directory.routeDetail(position.learningRouteId());
        if (detail == null) {
            return null;
        }
        List<CourseItem> all = detail.stages().stream().flatMap(stage -> stage.courses().stream()).toList();
        CourseItem target = all.stream().filter(course -> course.id().equals(position.courseId()))
                .findFirst().orElse(null);
        StageDetail stage = detail.stages().stream()
                .filter(one -> one.id().equals(position.routeStageId())).findFirst().orElse(null);
        if (target == null || stage == null) {
            return null;
        }
        int total = all.size();
        int learned = (int) all.stream().filter(CourseItem::isLearned).count();
        int mastered = (int) all.stream().filter(CourseItem::isMastered).count();
        int configured = (int) all.stream().filter(course -> course.practiceQuestionCount() > 0).count();
        List<StageCourse> stageCourses = stage.courses().stream()
                .map(course -> new StageCourse(course.id(), course.title(), course.isLearned(), course.isMastered()))
                .toList();
        return new ContinueLearningView(detail.id(), detail.name(), stage.id(), stage.name(),
                target.id(), target.title(), target.isLearned(), target.isMastered(),
                new RouteProgress(learned, total, mastered, configured), stageCourses);
    }
}
