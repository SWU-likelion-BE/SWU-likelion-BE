package com.afterme.backend.global.exception;

import com.afterme.backend.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //서비스에서 발생시킨 비즈니스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception
    ){
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    //@Valid 요청 DTO 검증 실패
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ){
        List<ErrorResponse.FieldErrorDetail> errors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new ErrorResponse.FieldErrorDetail(
                                error.getField(),
                                error.getDefaultMessage()
                        ))
                        .toList();

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                errors
        );

        return handleExceptionInternal(
                exception, response, headers, status, request
        );
    }

    //Spring MVC 기본 예외 -> 공통 형식 변환
    @Override
    protected  ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ){
        Object response = body;

        if(!(body instanceof ErrorResponse)){
            if(status.is5xxServerError()){
                log.error("Spring MVC 서버 오류", exception);

                response = ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                );
            } else{
                response = ErrorResponse.of(
                        "HTTP_" + status.value(),
                        "요청을 처리할 수 없습니다. 요청 내용을 확인해주세요.");
            }
        }

        return super.handleExceptionInternal(
                exception, response, headers, status, request
        );
    }

    //별도로 처리하지 않은 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception
    ){
        log.error("예상치 못한 서버 오류", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }
}
