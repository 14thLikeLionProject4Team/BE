package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "미션 상세 조회", description = "미션 한 건의 상세와 제출 이력(최신순, 사진 포함). 자녀·부모 공용. 다른 가족의 미션은 404.")
    @GetMapping("/missions/{missionId}")
    public ApiResponse<MissionDetailResponse> getMission(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long missionId) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_DETAIL_OK, missionService.getMission(personaId, missionId));
    }
}
