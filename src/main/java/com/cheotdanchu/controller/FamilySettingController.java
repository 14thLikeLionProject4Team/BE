package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.FamilySettingDtos;
import com.cheotdanchu.service.FamilySettingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "열람 범위 조회",
            description = "자녀·부모 공용. 부모가 아이 거래를 보는 범위(visibility, label)와 바꿀 수 있는 사람(changeableBy=PARENT)을 보여줍니다. 지금은 TOTAL_ONLY(총액만) 하나뿐입니다. 자녀는 확인만 하고 바꾸는 건 부모입니다. 가족 설정이 없으면 기본값 TOTAL_ONLY.")
    @GetMapping("/settings/visibility")
    public ApiResponse<FamilySettingDtos.VisibilityResponse> getVisibility(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.VISIBILITY_OK, familySettingService.getVisibility(personaId));
    }

    @Operation(summary = "열람 범위 변경",
            description = "부모 전용(자녀는 403). 열람 범위를 바꿉니다. 현재는 TOTAL_ONLY 만 유효하고 다른 값은 400(\"visibility: 현재는 TOTAL_ONLY만 선택할 수 있어요\"), 값이 없으면 400 입니다.")
    @PatchMapping("/settings/visibility")
    public ApiResponse<FamilySettingDtos.VisibilityChangeResponse> updateVisibility(
            @RequestHeader("X-Persona-Id") Long personaId,
            @Valid @RequestBody FamilySettingDtos.VisibilityRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.VISIBILITY_UPDATED, familySettingService.updateVisibility(personaId, request.visibility()));
    }
}
