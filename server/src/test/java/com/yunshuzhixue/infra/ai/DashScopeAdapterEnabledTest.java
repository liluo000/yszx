package com.yunshuzhixue.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 凭证存在时装配真实 SDK 适配器（只校验 bean 类型，不触发外部调用）。
 * 用假 key 验证条件分支，真实联调需替换为有效 key。
 */
@SpringBootTest
@TestPropertySource(properties = "yunshu.ai.dashscope.api-key=sk-test-placeholder")
class DashScopeAdapterEnabledTest {

    @Autowired
    private InterviewAi interviewAi;
    @Autowired
    private ShortAnswerScorer shortAnswerScorer;

    @Test
    void usesDashScopeWhenCredentialPresent() {
        assertThat(interviewAi).isInstanceOf(DashScopeInterviewAi.class);
        assertThat(shortAnswerScorer).isInstanceOf(DashScopeShortAnswerScorer.class);
    }
}
