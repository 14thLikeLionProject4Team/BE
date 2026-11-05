package com.cheotdanchu.common.response;

public record ApiResponse<T>(boolean success, int code, String message, T data) {

    public static <T> ApiResponse<T> onSuccess(SuccessStatus status, T data) {
        return new ApiResponse<>(true, status.getCode(), status.getMessage(), data);
    }
}
