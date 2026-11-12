package com.afterme.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            "입력값을 확인해주세요."
    ),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "로그인이 필요합니다."
    ),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "접근 권한이 없습니다."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "요청한 대상을 찾을 수 없습니다."
    ),
    CONFLICT(
            HttpStatus.CONFLICT,
            "CONFLICT",
            "현재 상태에서는 요청을 처리할 수 없습니다."
    ),

    // 인증
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "DUPLICATE_EMAIL",
            "이미 가입된 이메일입니다."
    ),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),
    INVALID_ACCESS_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_ACCESS_TOKEN",
            "액세스 토큰이 없거나 유효하지 않습니다."
    ),
    ACCOUNT_LOCKED(
            HttpStatus.LOCKED,
            "ACCOUNT_LOCKED",
            "로그인 실패 횟수를 초과하여 계정이 잠겼습니다. 잠시 후 다시 시도해주세요."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message){
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus(){
        return status;
    }

    public String getCode(){
        return code;
    }

    public String getMessage(){
        return message;
    }

}
