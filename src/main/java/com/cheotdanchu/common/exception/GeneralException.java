package com.cheotdanchu.common.exception;

import com.cheotdanchu.common.response.ErrorStatus;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final ErrorStatus status;

    public GeneralException(ErrorStatus status) {
        super(status.getMessage());
        this.status = status;
    }
}
