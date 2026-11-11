package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus {

    PERSONA_HEADER_MISSING(400, "X-Persona-Id 헤더가 필요합니다"),
    INVALID_MISSION_STATUS(400, "지원하지 않는 미션 상태입니다"),
    PERSONA_NOT_FOUND(404, "페르소나를 찾을 수 없습니다"),
    MISSION_NOT_FOUND(404, "미션을 찾을 수 없습니다");

    private final int code;
    private final String message;
}
