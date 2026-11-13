package com.cheotdanchu.common.response;

public record ApiResponse<T>(boolean success, int code, String message, T data) {

    public static <T> ApiResponse<T> onSuccess(SuccessStatus status, T data) {
        return onSuccess(status, status.getMessage(), data);
    }

    /** 금액처럼 상황마다 바뀌는 값을 메시지에 넣을 때 */
    public static <T> ApiResponse<T> onSuccess(SuccessStatus status, String message, T data) {
        return new ApiResponse<>(true, status.getCode(), message, data);
    }

    public static <T> ApiResponse<T> onFailure(ErrorStatus status) {
        return onFailure(status, status.getMessage());
    }

    public static <T> ApiResponse<T> onFailure(ErrorStatus status, String message) {
        return new ApiResponse<>(false, status.getCode(), message, null);
    }
}
