package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.dto.MissionSubmitResponse;
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

@RestController
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @Operation(summary = "진행 중인 미션 목록", description = "status=active. NEW·IN_PROGRESS·SUBMITTED 미션과 진행률(승인된 제출 / 목표 횟수). 자녀·부모 공용.")
    @GetMapping("/missions")
    public ApiResponse<MissionListResponse> getMissions(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam String status) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_ACTIVE_LIST_OK, missionService.getMissions(personaId, status));
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

    @Operation(summary = "미션 상세 조회", description = "미션 한 건의 상세와 제출 이력(최신순, 사진 포함). 자녀·부모 공용. 다른 가족의 미션은 404.")
    @GetMapping("/missions/{missionId}")
    public ApiResponse<MissionDetailResponse> getMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_DETAIL_OK, missionService.getMission(personaId, missionId));
    }
}
