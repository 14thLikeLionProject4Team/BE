package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus {

    PERSONA_HEADER_MISSING(400, "X-Persona-Id 헤더가 필요합니다"),
    INVALID_MISSION_STATUS(400, "지원하지 않는 미션 상태입니다"),
    PERSONA_NOT_FOUND(404, "페르소나를 찾을 수 없습니다"),
    MISSION_NOT_FOUND(404, "미션을 찾을 수 없습니다"),
    INVALID_REQUEST_VALUE(400, "요청 값의 형식이 올바르지 않습니다"),
    CHILD_ONLY(403, "자녀만 조회할 수 있어요"),
    PARENT_ONLY(403, "부모만 할 수 있어요"),
    INVALID_INPUT(400, "입력값을 확인해 주세요"),
    INVALID_PERIOD(400, "끝나는 날은 시작일 이후여야 해요"),
    ACCOUNT_NOT_FOUND(404, "연결된 계좌를 찾을 수 없습니다");

    private final int code;
    private final String message;
}
