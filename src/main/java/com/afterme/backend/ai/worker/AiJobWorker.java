package com.afterme.backend.ai.worker;

import com.afterme.backend.ai.client.OpenAiClient;
import com.afterme.backend.ai.config.AiProperties;
import com.afterme.backend.ai.job.AiJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * DB 기반 AI 작업 워커. 주기적으로 멈춘 작업을 복구하고 대기 작업을 하나씩 처리한다.
 * ai.worker.enabled=true일 때만 등록된다(테스트에서는 꺼짐).
 */
@Component
@ConditionalOnProperty(prefix = "ai.worker", name = "enabled", havingValue = "true")
public class AiJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);

    private final List<AiJobHandler> handlers;
    private final OpenAiClient openAi;
    private final AiProperties.Worker props;
    private final Clock clock;

    public AiJobWorker(List<AiJobHandler> handlers, OpenAiClient openAi, AiProperties properties, Clock clock) {
        this.handlers = handlers;
        this.openAi = openAi;
        this.props = properties.worker();
        this.clock = clock;
        if (!openAi.isConfigured()) {
            log.warn("OPENAI_API_KEY가 없어 AI 작업 워커가 대기 작업을 처리하지 않습니다.");
        }
    }

    @Scheduled(fixedDelayString = "${ai.worker.poll-delay-ms:5000}",
            initialDelayString = "${ai.worker.poll-delay-ms:5000}")
    public void run() {
        if (!openAi.isConfigured()) return;
        Instant staleBefore = clock.instant().minus(props.staleAfter());
        for (AiJobHandler handler : handlers) {
            recover(handler, staleBefore);
            processQueued(handler);
        }
    }

    private void recover(AiJobHandler handler, Instant staleBefore) {
        for (Long id : handler.staleIds(staleBefore, props.batchSize())) {
            try {
                handler.recoverStale(id, staleBefore, props.maxAttempts());
            } catch (RuntimeException e) {
                log.error("AI 작업 복구 실패 jobId={}-{} error={}", handler.prefix(), id, e.getClass().getSimpleName());
            }
        }
    }

    private void processQueued(AiJobHandler handler) {
        for (Long id : handler.queuedIds(props.batchSize())) {
            try {
                handler.process(id, props.maxAttempts());
            } catch (RuntimeException e) {
                log.error("AI 작업 처리 중 예외 jobId={}-{} error={}", handler.prefix(), id, e.getClass().getSimpleName());
            }
        }
    }
}