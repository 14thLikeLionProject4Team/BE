package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.dto.ShortfallCheckResponse;
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
            description = "자녀 전용. 이번 정산 기간의 결제일·상태·청구 예정액·이번 달 쓸 수 있는 돈(용돈+미션 보상)·지난달까지 남은 돈·부족 예상액을 반환합니다. 부족 예상액은 남은 돈을 포함하지 않습니다. 진행 중인 정산이 없는 가족(성년 전환 후 등)은 에러가 아니라 data가 null입니다.")
    @GetMapping("/settlements/current")
    public ApiResponse<CurrentSettlementResponse> getCurrent(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.SETTLEMENT_CURRENT_OK, settlementService.getCurrent(personaId));
    }

    @Operation(summary = "부족 예상 점검 조회",
            description = "자녀 전용. 결제일 D-6부터 결제일까지, 이번 달 쓸 수 있는 돈이 청구 예정액보다 적을 때만 visible=true. 지난달까지 남은 돈으로 메울 수 있어도 표시하며 안내(guides)에 알립니다. 표시하지 않을 때는 shortfallAmount 0, message null, guides 빈 배열. 진행 중인 정산이 없으면 에러가 아니라 visible=false이고 dueDate·dDay는 null, billedAmount는 0입니다.")
    @GetMapping("/settlements/current/check")
    public ApiResponse<ShortfallCheckResponse> getCheck(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.SHORTFALL_CHECK_OK, settlementService.getCheck(personaId));
    }
}
