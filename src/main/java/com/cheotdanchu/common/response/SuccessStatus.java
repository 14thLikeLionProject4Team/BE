package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessStatus {

    PERSONA_LIST_OK(200, "페르소나 목록 조회 성공"),
    MISSION_ACTIVE_LIST_OK(200, "진행 중인 미션 목록 조회 성공"),
    SETTLEMENT_CURRENT_OK(200, "조회 성공");

    private final int code;
    private final String message;
}
