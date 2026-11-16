package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.FamilySettingDtos;
import com.cheotdanchu.service.FamilySettingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FamilySettingController {

    private final FamilySettingService familySettingService;

    @Operation(summary = "미션 상한 변경",
            description = "부모 전용. 한 달 미션 보상 상한을 용돈 대비 비율(1~100%)로 바꿉니다. 이미 만든 미션은 그대로이고, 이후 미션 생성·열기·승인의 남은 한도 계산부터 새 비율을 씁니다. capAmount 는 월 보상 합계와 같은 계산입니다.")
    @PatchMapping("/family-settings/mission-cap")
    public ApiResponse<FamilySettingDtos.MissionCapResponse> updateMissionCap(
            @RequestHeader("X-Persona-Id") Long personaId,
            @Valid @RequestBody FamilySettingDtos.MissionCapRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MISSION_CAP_UPDATED, familySettingService.updateMissionCap(personaId, request.capRate()));
    }
}
