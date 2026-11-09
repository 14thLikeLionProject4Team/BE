package com.cheotdanchu.common.advice;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.ErrorStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    private ResponseEntity<ApiResponse<Void>> fail(ErrorStatus status) {
        return ResponseEntity.status(status.getCode()).body(ApiResponse.onFailure(status));
    }
}
