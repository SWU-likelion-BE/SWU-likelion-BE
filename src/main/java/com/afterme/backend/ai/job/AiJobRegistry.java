package com.afterme.backend.ai.job;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 외부 작업 ID 접두사로 처리기를 찾는다. 예: "quiz-12" → prefix "quiz" 처리기
 */
@Component
public class AiJobRegistry {

    private final Map<String, AiJobHandler> handlers;

    public AiJobRegistry(List<AiJobHandler> handlers) {
        // 접두사가 겹치면 서버 시작 시점에 바로 실패하도록 toUnmodifiableMap 사용
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(AiJobHandler::prefix, h -> h));
    }

    public AiJobHandler find(String jobId) {
        int dash = jobId == null ? -1 : jobId.indexOf('-');
        AiJobHandler handler = dash > 0 ? handlers.get(jobId.substring(0, dash)) : null;
        if (handler == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return handler;
    }
}