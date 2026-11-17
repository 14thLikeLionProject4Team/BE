package com.cheotdanchu.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus {

    PERSONA_HEADER_MISSING(400, "X-Persona-Id 헤더가 필요합니다"),
    INVALID_MISSION_STATUS(400, "지원하지 않는 미션 상태입니다"),
    INVALID_SUBMISSION_STATUS(400, "지원하지 않는 제출 상태입니다"),
    PERSONA_NOT_FOUND(404, "페르소나를 찾을 수 없습니다"),
    MISSION_NOT_FOUND(404, "미션을 찾을 수 없습니다"),
    INVALID_REQUEST_VALUE(400, "요청 값의 형식이 올바르지 않습니다"),
    CHILD_ONLY(403, "자녀만 조회할 수 있어요"),
    PARENT_ONLY(403, "부모만 할 수 있어요"),
    ADULT_TRANSITIONED(403, "기록이 본인에게 넘어갔어요."),
    INVALID_INPUT(400, "입력값을 확인해 주세요"),
    INVALID_PERIOD(400, "끝나는 날은 시작일 이후여야 해요"),
    REWARD_CAP_EXCEEDED(422, "이번 달 남은 한도를 넘어요"),
    PHOTO_REQUIRED(400, "인증 사진을 올려 주세요."),
    INVALID_PHOTO(400, "사진 파일(jpg, png, heic, heif, webp)만 올릴 수 있어요."),
    PHOTO_TOO_LARGE(400, "사진이 너무 커요. 10MB 이하로 올려 주세요."),
    MISSION_NOT_SUBMITTABLE(409, "지금은 제출할 수 없는 미션이에요."),
    MISSION_NOT_OPENABLE(409, "제안·보류·추천 미션만 열 수 있어요."),
    MISSION_NOT_HOLDABLE(409, "제안된 미션만 보류할 수 있어요."),
    MISSION_NOT_EDITABLE(409, "끝난 미션은 수정할 수 없어요."),
    MISSION_NOT_CANCELABLE(409, "끝난 미션은 취소할 수 없어요."),
    MISSION_NOT_PROPOSABLE(409, "이미 제안했거나 제안할 수 없는 미션이에요."),
    SUBMISSION_NOT_FOUND(404, "제출 기록을 찾을 수 없습니다"),
    NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다"),
    BRIEFING_NOT_FOUND(404, "브리핑을 찾을 수 없습니다"),
    ALREADY_REVIEWED(409, "이미 처리된 제출이에요."),
    ALREADY_REMINDED(409, "오늘은 이미 부모님께 알림을 보냈어요."),
    SUGGESTION_NOT_FOUND(404, "제안을 찾을 수 없습니다"),
    ALREADY_DECIDED(409, "이미 처리한 제안이에요."),
    SUGGESTION_OUTDATED(409, "용돈이 이미 바뀌어서 이 제안은 쓸 수 없어요."),
    FAMILY_SETTING_NOT_FOUND(404, "가족 약속 설정을 찾을 수 없습니다"),
    SETTLEMENT_NOT_FOUND(404, "정산을 찾을 수 없습니다"),
    NO_SHORTFALL(409, "보낼 부족분이 없어요"),
    INSUFFICIENT_CARRYOVER(400, "지난달까지 남은 돈이 부족해요"),
    ACCOUNT_NOT_FOUND(404, "연결된 계좌를 찾을 수 없습니다"),
    CHILD_MESSAGE_NOT_FOUND(404, "전할 말을 찾을 수 없습니다"),
    INVALID_STATUS_TRANSITION(409, "지금은 이 상태로 바꿀 수 없어요."),
    RETROSPECT_NOT_AVAILABLE(409, "결제일 정산이 끝난 뒤에 회고할 수 있어요."),
    AI_UNAVAILABLE(503, "지금은 답변하기 어려워요. 잠시 후 다시 물어봐 주세요.");

    private final int code;
    private final String message;
}
