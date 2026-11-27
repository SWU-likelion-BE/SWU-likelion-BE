package com.afterme.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 호출과 작업 워커 설정.
 *
 * @param openai OpenAI 연결 정보
 * @param worker 대기 작업 처리 워커 설정
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        OpenAi openai,
        Worker worker
) {
    /**
     * @param apiKey  API 키. 서버 환경변수로만 주입한다.
     * @param baseUrl API 기본 주소
     * @param model   사용할 모델명
     * @param timeout 응답 대기 최대 시간
     */
    public record OpenAi(String apiKey, String baseUrl, String model, Duration timeout) {
    }

    /**
     * @param enabled     워커 실행 여부. 테스트에서 작업이 자동 처리되지 않도록 기본값은 false.
     * @param batchSize   한 번에 꺼내 처리할 작업 수
     * @param maxAttempts 실패 후 자동 재시도 포함 최대 시도 횟수
     * @param staleAfter  RUNNING 상태가 이 시간을 넘으면 멈춘 작업으로 본다
     */
    public record Worker(boolean enabled, int batchSize, int maxAttempts, Duration staleAfter) {
    }
}