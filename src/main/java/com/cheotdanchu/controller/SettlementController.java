package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "현재 정산 조회",
            description = "자녀 전용. 이번 정산 기간의 결제일·상태·청구 예정액·이번 달 쓸 수 있는 돈(용돈+미션 보상)·지난달까지 남은 돈·부족 예상액을 반환합니다. 부족 예상액은 남은 돈을 포함하지 않습니다.")
    @GetMapping("/settlements/current")
    public ApiResponse<CurrentSettlementResponse> getCurrent(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.SETTLEMENT_CURRENT_OK, settlementService.getCurrent(personaId));
    }
}
