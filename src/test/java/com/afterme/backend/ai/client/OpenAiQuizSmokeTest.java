package com.afterme.backend.ai.client;

import com.afterme.backend.ai.config.AiProperties;
import com.afterme.backend.ai.prompt.QuizPrompt;
import com.afterme.backend.learning.port.LearningDocumentReader.Section;
import com.afterme.backend.quiz.service.QuizGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 OpenAI 호출 확인용. OPENAI_API_KEY 환경변수가 있을 때만 실행된다(없으면 건너뜀).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiQuizSmokeTest {

    @Test
    void realOpenAiReturnsQuizMatchingSchema() {
        var client = new OpenAiClient(new AiProperties(new AiProperties.OpenAi(
                System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1", "gpt-4o-mini", Duration.ofSeconds(60)), null));
        var input = new QuizGenerationService.Input(1L, 1L, 10L, List.of(
                new Section(11L, "PROCEDURE", "회계 보고서는 매달 25일까지 학생처에 제출한다. 영수증은 20일까지 총무에게 받는다."),
                new Section(12L, "EXCEPTIONS", "영수증이 누락되면 지출 사유서를 대신 첨부하고 회장 서명을 받는다.")));

        var output = QuizPrompt.toOutput(client.request(QuizPrompt.SYSTEM, QuizPrompt.user(input),
                QuizPrompt.SCHEMA_NAME, QuizPrompt.SCHEMA));

        output.questions().forEach(q -> System.out.printf("[%d] %s %s 정답=%d%n", q.sectionId(), q.stem(), q.options(), q.correctOption()));
        assertThat(output.questions()).isNotEmpty().allSatisfy(q -> {
            assertThat(q.sectionId()).isIn(11L, 12L);
            assertThat(q.options()).hasSize(4);
            assertThat(q.correctOption()).isBetween(0, 3);
        });
    }
}