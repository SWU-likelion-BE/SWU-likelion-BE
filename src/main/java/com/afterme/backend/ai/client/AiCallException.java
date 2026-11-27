package com.afterme.backend.ai.client;

/**
 * AI 호출 실패. code는 작업 실패 기록에 그대로 저장되므로 대문자·숫자·밑줄만 사용한다.
 */
public class AiCallException extends RuntimeException {
    private final String code;

    public AiCallException(String code) {
        super(code);
        this.code = code;
    }

    public AiCallException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 다시 시도하면 성공할 수 있는 실패인지. 키 오류·거부·요청 형식 오류는 다시 해도 같다. */
    public boolean isRetryable() {
        return switch (code) {
            case "AI_TIMEOUT", "AI_NETWORK_ERROR", "AI_RATE_LIMITED",
                 "AI_OUTPUT_TRUNCATED", "AI_OUTPUT_INVALID" -> true;
            default -> code.startsWith("AI_HTTP_5");
        };
    }
}