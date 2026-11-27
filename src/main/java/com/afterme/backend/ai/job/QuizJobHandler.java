package com.afterme.backend.ai.job;

import com.afterme.backend.ai.client.AiCallException;
import com.afterme.backend.ai.client.OpenAiClient;
import com.afterme.backend.ai.prompt.QuizPrompt;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.quiz.service.QuizGenerationService;
import com.afterme.backend.quiz.service.QuizGenerationService.Output;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.time.Instant;
import java.util.List;

/**
 * 퀴즈 생성 작업 처리기. 상태 변경·검증·저장은 QuizGenerationService가 하고, 여기서는 AI 호출과 실패 분류만 한다.
 */
@Component
@RequiredArgsConstructor
public class QuizJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(QuizJobHandler.class);

    private final QuizGenerationService quizzes;
    private final OpenAiClient openAi;

    @Override
    public String prefix() {
        return "quiz";
    }

    @Override
    public List<Long> queuedIds(int limit) {
        return quizzes.queuedIds(limit);
    }

    @Override
    public List<Long> staleIds(Instant staleBefore, int limit) {
        return quizzes.staleIds(staleBefore, limit);
    }

    @Override
    public void recoverStale(Long id, Instant staleBefore, int maxAttempts) {
        quizzes.recoverStale(id, staleBefore, maxAttempts);
    }

    @Override
    public void process(Long id, int maxAttempts) {
        var input = quizzes.claim(id);
        if (input.isEmpty()) return; // 다른 워커가 먼저 가져갔거나 이미 처리됨

        Output output;
        try {
            var result = openAi.request(QuizPrompt.SYSTEM, QuizPrompt.user(input.get()),
                    QuizPrompt.SCHEMA_NAME, QuizPrompt.SCHEMA);
            output = QuizPrompt.toOutput(result);
        } catch (AiCallException e) {
            handleFailure(id, e.getCode(), e.isRetryable(), maxAttempts);
            return;
        } catch (JacksonException e) {
            handleFailure(id, "AI_OUTPUT_INVALID", true, maxAttempts);
            return;
        }

        try {
            quizzes.complete(id, output);
        } catch (BusinessException e) {
            // INVALID_INPUT: AI 응답이 검증 실패 → 재요청. 그 외: 문서 공개 취소 등 다시 해도 안 되는 경우
            boolean invalidOutput = e.getErrorCode() == ErrorCode.INVALID_INPUT;
            handleFailure(id, invalidOutput ? "AI_OUTPUT_INVALID" : "QUIZ_SOURCE_UNAVAILABLE", invalidOutput, maxAttempts);
        }
    }

    @Override
    public Object status(Long userId, Long projectId, String jobId) {
        return quizzes.status(userId, projectId, jobId);
    }

    @Override
    public Object retry(Long userId, Long projectId, String jobId) {
        return quizzes.retry(userId, projectId, jobId);
    }

    private void handleFailure(Long id, String code, boolean retryable, int maxAttempts) {
        log.warn("퀴즈 생성 작업 실패 jobId=quiz-{} code={} retryable={}", id, code, retryable);
        if (retryable) quizzes.retryOrFail(id, code, maxAttempts);
        else quizzes.fail(id, code);
    }
}