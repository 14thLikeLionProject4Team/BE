package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessStatus {

    PERSONA_LIST_OK(200, "페르소나 목록 조회 성공"),
    NOTIFICATION_LIST_OK(200, "알림 목록 조회 성공"),
    NOTIFICATION_READ(200, "알림을 읽음 처리했어요."),
    PARENT_HOME_OK(200, "부모 홈 조회 성공"),
    DECISIONS_OK(200, "결정 카드 조회 성공"),
    MESSAGE_SENT(201, "메시지 전송 성공"),
    CONVERSATION_STARTER_OK(200, "대화 시작 정보 조회 성공"),
    CONVERSATION_MESSAGES_OK(200, "대화 기록 조회 성공"),
    CONVERSATION_DELETED(200, "대화 기록을 지웠어요."),
    CHILD_MESSAGE_UPDATED(200, "전할 말 상태를 바꿨어요."),
    MISSION_ACTIVE_LIST_OK(200, "진행 중인 미션 목록 조회 성공"),
    MISSION_DETAIL_OK(200, "미션 상세 조회 성공"),
    MISSION_PROPOSED_LIST_OK(200, "제안 미션 목록 조회 성공"),
    MISSION_ENDED_LIST_OK(200, "종료 미션 목록 조회 성공"),
    PENDING_SUBMISSIONS_OK(200, "승인 대기 목록 조회 성공"),
    REWARD_SUMMARY_OK(200, "월 보상 합계 조회 성공"),
    MISSION_CREATED(201, "미션을 열었어요. 아이에게 바로 알림이 가요."),
    MISSION_PROPOSED(201, "부모님께 미션을 제안했어요."),
    MISSION_OPENED(200, "미션을 열었어요. 아이에게 바로 알림이 가요."),
    MISSION_HELD(200, "제안을 보류했어요."),
    MISSION_SUBMITTED(201, "완료를 제출했어요. 부모님 확인을 기다려요."),
    SUBMISSION_APPROVED(200, "보상을 보냈어요."),
    SUBMISSION_REJECTED(200, "아이에게 다시 부탁했어요."),
    SETTLEMENT_CURRENT_OK(200, "조회 성공"),
    SHORTFALL_CHECK_OK(200, "부족 예상 조회 성공"),
    CHILD_SPENDING_OK(200, "정산 요약 조회 성공"),
    RECORDS_SUMMARY_OK(200, "조회 성공"),
    ALLOWANCE_SUGGESTION_OK(200, "AI 용돈 제안 조회 성공"),
    ALLOWANCE_SUGGESTION_APPLIED(200, "용돈 변경을 적용했어요."),
    ALLOWANCE_SUGGESTION_KEPT(200, "지금 용돈을 유지해요."),
    ALLOWANCE_SETUP_OK(200, "조회 성공"),
    ALLOWANCE_PREVIEW_OK(200, "조회 성공"),
    MISSION_CAP_UPDATED(200, "미션 상한을 바꿨어요"),
    ALLOWANCE_CHANGE_SAVED(201, "용돈을 저장했어요"),
    ALLOWANCE_SETTING_SAVED(200, "설정을 저장했어요. 용돈 금액은 그대로예요.");

    private final int code;
    private final String message;
}
