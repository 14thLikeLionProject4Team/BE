package com.cheotdanchu.common.advice;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.ErrorStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handle(GeneralException e) {
        return fail(e.getStatus(), e.getMessage());
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

    /** @Valid 검증 실패(필수값 누락 등), 본문 JSON 형식 오류(잘못된 enum·날짜 등) */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        return fail(ErrorStatus.INVALID_INPUT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MaxUploadSizeExceededException e) {
        return fail(ErrorStatus.PHOTO_TOO_LARGE);
    }

    private ResponseEntity<ApiResponse<Void>> fail(ErrorStatus status) {
        return fail(status, status.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> fail(ErrorStatus status, String message) {
        return ResponseEntity.status(status.getCode()).body(ApiResponse.onFailure(status, message));
    }
}
