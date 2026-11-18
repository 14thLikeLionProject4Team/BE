package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.NotificationSettingDtos;
import com.cheotdanchu.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService settingService;

    @Operation(summary = "알림 설정 조회",
            description = "자녀·부모 공용. 호출한 사람 본인의 알림 항목(type, label)과 켜짐 여부(enabled)를 화면 순서대로 보여줍니다. 항목 종류는 자녀(결제일 정산 결과·부족 예상·용돈 변경·새 미션·미션 보상·미션 반려)와 부모(결제일 정산 결과·부족 예상·미션 제안·미션 확인 재요청·위험 결제·성년 전환)가 다릅니다. 바꾼 적 없는 항목은 기본값(켜짐, 부모의 부족 예상만 꺼짐)입니다. 결제일 정산 결과 항목 하나가 정산 결과·부족분 해소·자동 정산 알림을 함께 켜고 끕니다.")
    @GetMapping("/notification-settings")
    public ApiResponse<NotificationSettingDtos.ListResponse> getSettings(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.NOTIFICATION_SETTINGS_OK, settingService.getSettings(personaId));
    }

    @Operation(summary = "알림 설정 변경",
            description = "자녀·부모 공용. 내 알림 항목 하나를 켜고 끕니다(enabled). 끈 항목은 이후 그 알림이 만들어지지 않습니다. 없는 항목이거나 내 역할에 없는 항목이면 404(\"알 수 없는 알림 항목이에요\"), enabled 가 없으면 400 입니다.")
    @PatchMapping("/notification-settings/{type}")
    public ApiResponse<NotificationSettingDtos.ChangeResponse> update(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable String type,
            @Valid @RequestBody NotificationSettingDtos.ChangeRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.NOTIFICATION_SETTING_UPDATED, settingService.update(personaId, type, request.enabled()));
    }
}
