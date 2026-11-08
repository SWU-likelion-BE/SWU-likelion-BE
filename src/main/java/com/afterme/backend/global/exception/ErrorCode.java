package com.afterme.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            "입력값을 확인해주세요."
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
