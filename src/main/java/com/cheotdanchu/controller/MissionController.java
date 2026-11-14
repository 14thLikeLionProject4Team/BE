package com.cheotdanchu.controller;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionQueryDtos;
import com.cheotdanchu.dto.MissionSubmitResponse;
import com.cheotdanchu.dto.SubmissionReviewDtos;
import com.cheotdanchu.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "미션 목록 (진행 중 / 제안 / 종료)",
            description = "status=active: NEW·IN_PROGRESS·SUBMITTED 미션과 진행률. "
                    + "status=PROPOSED: 아이가 제안한 미션 (자녀는 보류 포함). "
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

    @Operation(summary = "미션 완료 제출", description = "자녀 전용. multipart/form-data, photos(사진 인증 미션이면 1장 이상). NEW·IN_PROGRESS만 제출 가능, 제출하면 SUBMITTED.")
    @PostMapping(value = "/missions/{missionId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionSubmitResponse> submitMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_SUBMITTED, missionService.submitMission(personaId, missionId, photos));
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

    @Operation(summary = "미션 상세 조회", description = "미션 한 건의 상세와 제출 이력(최신순, 사진 포함). 자녀·부모 공용. 다른 가족의 미션은 404.")
    @GetMapping("/missions/{missionId}")
    public ApiResponse<MissionDetailResponse> getMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_DETAIL_OK, missionService.getMission(personaId, missionId));
    }
}
