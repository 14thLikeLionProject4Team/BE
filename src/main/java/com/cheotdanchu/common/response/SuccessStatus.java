package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessStatus {

    PERSONA_LIST_OK(200, "페르소나 목록 조회 성공"),
    MISSION_ACTIVE_LIST_OK(200, "진행 중인 미션 목록 조회 성공"),
    MISSION_DETAIL_OK(200, "미션 상세 조회 성공"),
    MISSION_CREATED(201, "미션을 열었어요. 아이에게 바로 알림이 가요."),
    MISSION_SUBMITTED(201, "완료를 제출했어요. 부모님 확인을 기다려요."),
    SETTLEMENT_CURRENT_OK(200, "조회 성공"),
    SHORTFALL_CHECK_OK(200, "부족 예상 조회 성공"),
    CHILD_SPENDING_OK(200, "정산 요약 조회 성공"),
    RECORDS_SUMMARY_OK(200, "조회 성공");

    private final int code;
    private final String message;
}
