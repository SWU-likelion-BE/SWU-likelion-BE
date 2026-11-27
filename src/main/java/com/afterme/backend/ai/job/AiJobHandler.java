package com.afterme.backend.ai.job;

import java.time.Instant;
import java.util.List;

/**
 * AI 작업 종류(퀴즈·추가 질문·문서 생성)별 처리기. 워커와 공통 작업 API가 이 인터페이스로만 접근한다.
 */
public interface AiJobHandler {

    /** 외부 작업 ID 접두사. 예: "quiz-12"의 "quiz" */
    String prefix();

    List<Long> queuedIds(int limit);

    List<Long> staleIds(Instant staleBefore, int limit);

    void recoverStale(Long id, Instant staleBefore, int maxAttempts);

    /** 작업 하나를 확보해 AI를 호출하고 결과를 저장한다. 트랜잭션 밖에서 호출해야 한다. */
    void process(Long id, int maxAttempts);

    /** 각 모듈의 작업 응답 DTO를 그대로 반환한다. 권한 검사는 각 모듈이 한다. */
    Object status(Long userId, Long projectId, String jobId);

    Object retry(Long userId, Long projectId, String jobId);
}