package com.cheotdanchu.controller;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionProposalRequest;
import com.cheotdanchu.dto.MissionUpdateRequest;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionQueryDtos;
import com.cheotdanchu.dto.MissionSubmitResponse;
import com.cheotdanchu.dto.SubmissionReviewDtos;
import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.service.MissionService;
import com.cheotdanchu.service.SavingTipService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;
    private final SavingTipService savingTipService;

    @Operation(summary = "미션 목록 (진행 중 / 제안 / 종료)",
            description = "status=active: NEW·IN_PROGRESS·SUBMITTED 미션과 진행률. "
                    + "status=PROPOSED: 아이가 제안한 미션 (보류 포함, status로 구분). "
                    + "status=ended: 완료·취소·만료 미션 최신순, cursor로 다음 페이지. 자녀·부모 공용.")
    @GetMapping("/missions")
    public ApiResponse<?> getMissions(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam String status,
            @RequestParam(required = false) Long cursor) {
        return switch (status) {
            case "active" -> ApiResponse.onSuccess(SuccessStatus.MISSION_ACTIVE_LIST_OK, missionService.getActiveMissions(personaId));
            case "PROPOSED" -> ApiResponse.onSuccess(SuccessStatus.MISSION_PROPOSED_LIST_OK, missionService.getProposedMissions(personaId));
            case "ended" -> ApiResponse.onSuccess(SuccessStatus.MISSION_ENDED_LIST_OK, missionService.getEndedMissions(personaId, cursor));
            default -> throw new GeneralException(ErrorStatus.INVALID_MISSION_STATUS);
        };
    }

    @Operation(summary = "승인 대기 미션 목록", description = "status=PENDING. 부모 확인을 기다리는 제출(사진 포함), 최신순. 자녀·부모 공용.")
    @GetMapping("/missions/submissions")
    public ApiResponse<MissionQueryDtos.PendingListResponse> getSubmissions(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam String status) {
        if (!"PENDING".equals(status)) throw new GeneralException(ErrorStatus.INVALID_SUBMISSION_STATUS);
        return ApiResponse.onSuccess(SuccessStatus.PENDING_SUBMISSIONS_OK, missionService.getPendingSubmissions(personaId));
    }

    @Operation(summary = "보상 가이드 조회", description = "자녀·부모 공용. 미션 보상 참고 구간(이번 정산 기간 용돈의 3~8%, 100원 단위 내림)과 이번 달 남은 한도, 빠른 선택 금액. 미션 만들기와 미션 제안 화면이 같이 쓴다. 용돈 설정이 없으면 금액은 null.")
    @GetMapping("/missions/reward-guide")
    public ApiResponse<MissionQueryDtos.RewardGuideResponse> getRewardGuide(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.REWARD_GUIDE_OK, missionService.getRewardGuide(personaId));
    }

    @Operation(summary = "월 보상 합계 조회", description = "이번 정산 기간에 받은 미션 보상과 한 달 상한(용돈 × 상한 비율), 남은 한도. 자녀·부모 공용.")
    @GetMapping("/missions/rewards/summary")
    public ApiResponse<MissionQueryDtos.RewardSummaryResponse> getRewardSummary(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.REWARD_SUMMARY_OK, missionService.getRewardSummary(personaId));
    }

    @Operation(summary = "미션 생성", description = "부모 전용. 새 미션을 바로 엽니다(NEW, origin PARENT). 자녀에게 NEW_MISSION 알림.")
    @PostMapping("/missions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionCreateResponse> createMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @Valid @RequestBody MissionCreateRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_CREATED, missionService.createMission(personaId, request));
    }

    @Operation(summary = "미션 제안", description = "자녀 전용. 미션 생성과 같은 필드인데 보상 대신 desiredReward(바라는 보상). PROPOSED로 저장, 부모에게 MISSION_PROPOSED 알림.")
    @PostMapping("/missions/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionCreateResponse> proposeMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @Valid @RequestBody MissionProposalRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_PROPOSED, missionService.proposeMission(personaId, request));
    }

    @Operation(summary = "추천 미션 제안", description = "자녀 전용. 자녀 화면 추천 미션(RECOMMENDED, recommend_to = CHILD)을 부모에게 제안한다 → PROPOSED, 추천 보상은 desiredReward로 넘어가고 부모에게 MISSION_PROPOSED 알림. 그 외 미션은 409.")
    @PostMapping("/missions/{missionId}/propose")
    public ApiResponse<MissionCreateResponse> proposeRecommendation(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.RECOMMENDATION_PROPOSED, missionService.proposeRecommendation(personaId, missionId));
    }

    @Operation(summary = "제안·추천 미션 오픈", description = "부모 전용. 제안(PROPOSED·ON_HOLD)·추천(RECOMMENDED) 미션을 미션 생성과 같은 필드로 채워 엽니다 → NEW, 자녀 알림. 그 외 상태는 409, 남은 한도 초과는 422.")
    @PatchMapping("/missions/{missionId}/open")
    public ApiResponse<MissionCreateResponse> openMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId,
            @Valid @RequestBody MissionCreateRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_OPENED, missionService.openMission(personaId, missionId, request));
    }

    @Operation(summary = "제안 보류", description = "부모 전용. 아이가 제안한 미션을 보류(\"다음에\"). PROPOSED → ON_HOLD, 그 외 상태는 409.")
    @PatchMapping("/missions/{missionId}/hold")
    public ApiResponse<MissionCreateResponse> holdMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_HELD, missionService.holdMission(personaId, missionId));
    }

    @Operation(summary = "미션 완료 제출", description = "자녀 전용. multipart/form-data, photos(사진 인증 미션이면 1장 이상). NEW·IN_PROGRESS만 제출 가능, 제출하면 SUBMITTED.")
    @PostMapping(value = "/missions/{missionId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionSubmitResponse> submitMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_SUBMITTED, missionService.submitMission(personaId, missionId, photos));
    }

    @Operation(summary = "승인 재요청", description = "자녀 전용. 확인을 기다리는 제출을 부모에게 다시 알린다(MISSION_REMIND, refId = submissionId). 이미 승인·반려된 제출은 409, 다른 가족의 제출은 404.")
    @PostMapping("/missions/submissions/{submissionId}/remind")
    public ApiResponse<Void> remindSubmission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long submissionId) {
        missionService.remindSubmission(personaId, submissionId);
        return ApiResponse.onSuccess(SuccessStatus.MISSION_REMINDED, null);
    }

    @Operation(summary = "미션 승인", description = "부모 전용. 보상을 자녀 계좌로 보내고(MISSION_REWARD 이체) 알림. 목표 횟수를 채우면 COMPLETED, 아니면 IN_PROGRESS. 이번 정산 기간 한도를 넘으면 422.")
    @PostMapping("/missions/submissions/{submissionId}/approve")
    public ApiResponse<SubmissionReviewDtos.ApproveResponse> approve(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long submissionId) {
        SubmissionReviewDtos.ApproveResponse result = missionService.approve(personaId, submissionId);
        return ApiResponse.onSuccess(SuccessStatus.SUBMISSION_APPROVED,
                String.format(Locale.KOREA, "%,d원을 보냈어요.", result.rewardAmount()), result);
    }

    @Operation(summary = "미션 반려", description = "부모 전용. reason: PHOTO_UNCLEAR | NOT_DONE. 보상 없이 미션을 IN_PROGRESS로 돌리고 사유와 함께 알림.")
    @PostMapping("/missions/submissions/{submissionId}/reject")
    public ApiResponse<SubmissionReviewDtos.RejectResponse> reject(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long submissionId,
            @Valid @RequestBody SubmissionReviewDtos.RejectRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.SUBMISSION_REJECTED,
                missionService.reject(personaId, submissionId, request.reason()));
    }

    @Operation(summary = "미션 수정", description = "부모 전용. 새 미션·진행 중·확인 대기 미션의 이름·완료 기준·보상·마감일 중 보낸 것만 바꾼다. 끝난 미션이나 아직 열지 않은 제안·추천은 409, 보상이 이번 달 남은 한도를 넘으면 422, 마감일이 시작일보다 앞서면 400.")
    @PatchMapping("/missions/{missionId}")
    public ApiResponse<MissionCreateResponse> updateMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId,
            @Valid @RequestBody MissionUpdateRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_UPDATED, missionService.updateMission(personaId, missionId, request));
    }

    @Operation(summary = "미션 취소", description = "부모 전용. 지우지 않고 CANCELED로 바꾸며 종료 미션 목록에 남는다. 새 미션·진행 중인 미션만 가능하고, 확인 대기(SUBMITTED)는 제출을 먼저 승인·반려해야 한다. 그 외 상태는 409.")
    @DeleteMapping("/missions/{missionId}")
    public ApiResponse<MissionCreateResponse> cancelMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_CANCELED, missionService.cancelMission(personaId, missionId));
    }

    @Operation(summary = "절약 팁 조회", description = "자녀 전용. 이번 정산 기간 결제에서 가장 많이 쓴 항목으로 만든 절약 팁 한 줄. 부족 예상 카드가 떠 있으면 몇 번 줄이면 부족분이 채워지는지 알려준다. link는 결제 내역 이동용 필터(항목, 금액순). 진행 중인 정산이나 결제가 없으면 data null.")
    @GetMapping("/missions/saving-tip")
    public ApiResponse<MissionQueryDtos.SavingTip> getSavingTip(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.SAVING_TIP_OK, savingTipService.getSavingTip(personaId));
    }

    @Operation(summary = "추천 미션 목록", description = "자녀·부모 공용. target=CHILD(자녀 화면) | PARENT(부모 화면), 내 역할과 같아야 한다(다르면 400). 10개씩, cursor는 이전 페이지의 nextCursor. 부모 화면 추천은 '제안·추천 미션 오픈'으로 열고, 자녀 화면 추천은 '추천 미션 제안'으로 부모에게 제안한다.")
    @GetMapping("/missions/recommendations")
    public ApiResponse<MissionQueryDtos.RecommendationListResponse> getRecommendations(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) Mission.RecommendTo target,
            @RequestParam(required = false) Long cursor) {
        return ApiResponse.onSuccess(SuccessStatus.RECOMMENDATIONS_OK, missionService.getRecommendations(personaId, target, cursor));
    }

    @Operation(summary = "미션 상세 조회", description = "미션 한 건의 상세와 제출 이력(최신순, 사진 포함). 자녀·부모 공용. 다른 가족의 미션은 404.")
    @GetMapping("/missions/{missionId}")
    public ApiResponse<MissionDetailResponse> getMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_DETAIL_OK, missionService.getMission(personaId, missionId));
    }
}
