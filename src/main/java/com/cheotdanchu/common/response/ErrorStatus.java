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
    ADULT_TRANSITIONED(403, "기록이 본인에게 넘어갔어요."),
    INVALID_INPUT(400, "입력값을 확인해 주세요"),
    INVALID_PERIOD(400, "끝나는 날은 시작일 이후여야 해요"),
    REWARD_CAP_EXCEEDED(422, "이번 달 남은 한도를 넘어요"),
    PHOTO_REQUIRED(400, "인증 사진을 올려 주세요."),
    INVALID_PHOTO(400, "사진 파일(jpg, png, heic, heif, webp)만 올릴 수 있어요."),
    PHOTO_TOO_LARGE(400, "사진이 너무 커요. 10MB 이하로 올려 주세요."),
    MISSION_NOT_SUBMITTABLE(409, "지금은 제출할 수 없는 미션이에요."),
    SUBMISSION_NOT_FOUND(404, "제출 기록을 찾을 수 없습니다"),
    ALREADY_REVIEWED(409, "이미 처리된 제출이에요."),
    SUGGESTION_NOT_FOUND(404, "제안을 찾을 수 없습니다"),
    ALREADY_DECIDED(409, "이미 처리한 제안이에요."),
    SUGGESTION_OUTDATED(409, "용돈이 이미 바뀌어서 이 제안은 쓸 수 없어요."),
    ACCOUNT_NOT_FOUND(404, "연결된 계좌를 찾을 수 없습니다");

    private final int code;
    private final String message;
}
