package com.afterme.backend.global.response;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> errors
) {

    public static ErrorResponse of (String code, String message){
        return new ErrorResponse(code, message,List.of());
    }

    public record FieldErrorDetail(
            String field,
            String message
    ){}
}
