package com.cheotdanchu.common.exception;

import com.cheotdanchu.common.response.ErrorStatus;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final ErrorStatus status;

    public GeneralException(ErrorStatus status) {
        this(status, status.getMessage());
    }

    /** 금액처럼 상황마다 바뀌는 값을 메시지에 넣을 때 */
    public GeneralException(ErrorStatus status, String message) {
        super(message);
        this.status = status;
    }
}
