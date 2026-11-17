package com.yunshuzhixue.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import com.yunshuzhixue.modules.ai.service.DevInterviewAi;
import com.yunshuzhixue.modules.ai.service.DevShortAnswerScorer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * AI 厂商适配层（架构 T05）：凭证为空时必须回退本地占位，保证离线可运行；
 * 提示词模板与按句切分逻辑单独校验，不依赖真实外部调用。
 */
@SpringBootTest
@TestPropertySource(properties = "yunshu.ai.dashscope.api-key=")
class DashScopeAdapterTest {

    @Autowired
    private InterviewAi interviewAi;
    @Autowired
    private ShortAnswerScorer shortAnswerScorer;

    @Test
    void fallsBackToDevWhenCredentialBlank() {
        assertThat(interviewAi).isInstanceOf(DevInterviewAi.class);
        assertThat(shortAnswerScorer).isInstanceOf(DevShortAnswerScorer.class);
    }

    @Test
    void splitsModelOutputIntoSentences() {
        DashScopeClient client = new DashScopeClient(new DashScopeProperties(null, null, null, null, null));
        List<String> sentences = client.toSentences(
                "你好，欢迎参加面试。我是本次的 AI 面试官？请开始作答！最后一点；结束。");
        assertThat(sentences).containsExactly(
                "你好，欢迎参加面试。",
                "我是本次的 AI 面试官？",
                "请开始作答！",
                "最后一点；结束。");
    }

    @Test
    void stripsMarkdownNumberingWhenSplitting() {
        DashScopeClient client = new DashScopeClient(new DashScopeProperties(null, null, null, null, null));
        List<String> sentences = client.toSentences("1. 第一句话。\n- 第二句话。");
        assertThat(sentences).containsExactly("第一句话。", "第二句话。");
    }
}
