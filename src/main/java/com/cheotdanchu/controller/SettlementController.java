package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.dto.SettlementHistoryDtos;
import com.cheotdanchu.dto.ShortfallCheckResponse;
import com.cheotdanchu.service.SettlementHistoryService;
import com.cheotdanchu.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementHistoryService settlementHistoryService;

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

    @Operation(summary = "정산 목록 조회 (결제일 기록)",
            description = "자녀 전용(부모는 403). 월별 결제일 결과를 최신순으로. 정산 전인 이번 달은 넣지 않습니다. result 는 FULL_PAID(완납)·SELF_RESOLVED(스스로 해소)·AUTO_DEDUCTED(자동 정산), 아직 끝나지 않았으면 null. isExcluded 는 기록 지표에서 빠진 정산. size 는 기본 20(최대 100), cursor 는 이전 응답의 nextCursor.")
    @GetMapping("/settlements")
    public ApiResponse<SettlementHistoryDtos.ListResponse> getHistory(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.onSuccess(SuccessStatus.SETTLEMENT_LIST_OK, settlementHistoryService.getHistory(personaId, size, cursor));
    }
}
