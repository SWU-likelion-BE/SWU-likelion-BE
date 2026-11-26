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
}