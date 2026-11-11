package com.cheotdanchu.common.advice;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.ErrorStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handle(GeneralException e) {
        return fail(e.getStatus());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MissingRequestHeaderException e) {
        return fail(ErrorStatus.PERSONA_HEADER_MISSING);
    }

    /** 숫자여야 하는 X-Persona-Id 등에 문자가 들어온 경우 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MethodArgumentTypeMismatchException e) {
        return fail(ErrorStatus.INVALID_REQUEST_VALUE);
    }

    private ResponseEntity<ApiResponse<Void>> fail(ErrorStatus status) {
        return ResponseEntity.status(status.getCode()).body(ApiResponse.onFailure(status));
    }
}
