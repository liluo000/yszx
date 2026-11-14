package com.yunshuzhixue.modules.portal.service;

import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.wrongbook.api.WrongQuestionCounter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 学员端首页（需求 5.2，S-PTL-01）。
 *
 * <p>待重做错题已接入 M10、今日面试剩余次数已接入 M13；继续学习与我的路线进度依赖 M08，
 * 尚未开发，先返回空。</p>
 */
@Service
public class StudentHomeService {

    /** 游客与已登录学员共用；个性化字段在游客时为空 */
    public record HomeView(boolean isGuest, Object continueLearning, List<RouteCard> myRoutes,
                           long pendingWrongQuestionCount, long interviewRemaining, List<RouteCard> routes) {
    }

    private final RouteDirectory directory;
    private final WrongQuestionCounter wrongQuestions;
    private final AiQuotaApi quota;

    public StudentHomeService(RouteDirectory directory, WrongQuestionCounter wrongQuestions, AiQuotaApi quota) {
        this.directory = directory;
        this.wrongQuestions = wrongQuestions;
        this.quota = quota;
    }

    public HomeView load() {
        boolean guest = !StudentStp.LOGIC.isLogin();
        List<RouteCard> published = directory.publishedRoutes();
        long pendingWrong = 0;
        long interviewRemaining = 0;
        if (!guest) {
            long studentId = StudentStp.LOGIC.getLoginIdAsLong();
            pendingWrong = wrongQuestions.countRedoable(studentId);
            interviewRemaining = quota.current(studentId).interviewRemaining();
        }
        return new HomeView(guest, null, List.of(), pendingWrong, interviewRemaining, published);
    }
}
