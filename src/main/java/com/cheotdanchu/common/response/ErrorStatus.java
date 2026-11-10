package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus {

    PERSONA_HEADER_MISSING(400, "X-Persona-Id 헤더가 필요합니다"),
    INVALID_MISSION_STATUS(400, "지원하지 않는 미션 상태입니다"),
    PERSONA_NOT_FOUND(404, "페르소나를 찾을 수 없습니다"),
    INVALID_REQUEST_VALUE(400, "요청 값의 형식이 올바르지 않습니다"),
    FORBIDDEN_ROLE(403, "자녀만 조회할 수 있어요"),
    ACCOUNT_NOT_FOUND(404, "연결된 계좌를 찾을 수 없습니다"),
    SETTLEMENT_NOT_FOUND(404, "현재 정산 기간이 없어요");

    private final int code;
    private final String message;
}
