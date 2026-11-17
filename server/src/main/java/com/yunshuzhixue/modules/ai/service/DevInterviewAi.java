package com.yunshuzhixue.modules.ai.service;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地开发用的 AI 面试官占位实现：固定角色与话术模板，按参考答案要点重合度估分。
 * 不调用任何外部服务，保证离线可运行；接入真实大模型后替换本实现（架构 T05）。
 *
 * <p>由 infra.ai 的 AiAdapterConfiguration 按凭证有无条件注册，不直接标注 @Service。</p>
 */
public class DevInterviewAi implements InterviewAi {

    private static final String ROLE = "我是本次的 AI 面试官，接下来会围绕你选择的路线提问。";

    @Override
    public List<String> opening(String routeName) {
        return List.of("你好，欢迎参加本次 AI 模拟面试。", ROLE,
                "本次面试围绕「" + routeName + "」展开，请尽量结合具体项目经验回答。",
                "每题结束后我会做简短追问，最后生成一份面试报告。");
    }

    @Override
    public List<String> mainQuestion(String stem, int seqNo, int total) {
        return List.of("第 " + seqNo + " 题，共 " + total + " 题。", stem,
                "请谈谈你的理解，最好能举一个实际例子。");
    }

    @Override
    public boolean needsFollowup(String studentAnswer, int followupCount, int maxFollowup) {
        if (followupCount >= maxFollowup || studentAnswer == null) {
            return false;
        }
        return studentAnswer.trim().length() < 120;
    }

    @Override
    public List<String> followup(String stem, String studentAnswer, int followupCount) {
        return List.of("谢谢，我顺着你的回答再追问一点。",
                "能不能结合这道题的关键点，补充一下具体做法、遇到的坑，以及你是怎么验证效果的？");
    }

    @Override
    public List<String> transition(int nextSeqNo, int total) {
        return List.of("这一题就到这里，我们进入下一题。", "接下来是第 " + nextSeqNo + " 题，共 " + total + " 题。");
    }

    @Override
    public List<String> closing() {
        return List.of("本次面试的所有题目已经完成，感谢你的配合。",
                "我会根据整场回答生成面试报告，稍后可以在「面试记录」里查看。");
    }

    @Override
    public QuestionScore score(String stem, String referenceAnswer, String scoringPoints, String allAnswers) {
        if (allAnswers == null || allAnswers.isBlank()) {
            return new QuestionScore(0, "本题未作答。", "未覆盖参考答案中的任何要点。");
        }
        String reference = scoringPoints == null || scoringPoints.isBlank() ? referenceAnswer : scoringPoints;
        Set<String> points = grams(reference == null ? "" : reference);
        Set<String> answer = grams(allAnswers);
        if (points.isEmpty()) {
            return new QuestionScore(60, "已作答；本题未配置评分要点，按及格线计分。", "建议对照参考答案补充细节。");
        }
        long hit = points.stream().filter(answer::contains).count();
        double ratio = (double) hit / points.size();
        // 占位评分：已作答给基础分，再按要点覆盖率上浮，避免与真实模型的分数分布差异过大
        int score = (int) Math.round(Math.min(100, 35 + ratio * 65));
        return new QuestionScore(score,
                "回答覆盖了参考答案约 " + Math.round(ratio * 100) + "% 的要点，表述" + (score >= 80 ? "较为完整" : "还有提升空间") + "。",
                "建议补充：" + missing(points, answer));
    }

    @Override
    public String advice(String stageName, double score) {
        return "「" + stageName + "」本场得分 " + String.format("%.1f", score)
                + " 分，建议回到该阶段的课程重新梳理核心概念，再通过该课程的练习检验掌握情况。";
    }

    /** 占位 ASR：未接入厂商时不产生转写文字，由 WS 的开发占位通道送文字 */
    @Override
    public String transcribe(byte[] audio) {
        return "";
    }

    private static String missing(Set<String> points, Set<String> answer) {
        List<String> left = new ArrayList<>();
        for (String point : points) {
            if (!answer.contains(point)) {
                left.add(point);
                if (left.size() >= 6) break;
            }
        }
        return left.isEmpty() ? "暂无明显缺失" : String.join("、", left);
    }

    /** 中文按相邻字符二元组、英文按词，粗略衡量要点覆盖 */
    private static Set<String> grams(String text) {
        String normalized = text.toLowerCase().replaceAll("[\\s\\p{Punct}，。；：、（）\"'`]+", "");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) {
            result.add(normalized.substring(i, i + 2));
        }
        if (result.isEmpty() && !normalized.isBlank()) {
            result.addAll(Arrays.asList(normalized.split(" ")));
        }
        return result;
    }
}
