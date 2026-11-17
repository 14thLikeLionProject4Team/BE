package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.dto.SettlementHistoryDtos;
import com.cheotdanchu.dto.ShortfallCheckResponse;
import com.cheotdanchu.dto.ShortfallPaymentDtos;
import com.cheotdanchu.service.SettlementHistoryService;
import com.cheotdanchu.service.SettlementService;
import com.cheotdanchu.service.ShortfallPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementHistoryService settlementHistoryService;
    private final ShortfallPaymentService shortfallPaymentService;

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

    @Operation(summary = "정산 상세 조회",
            description = "자녀 전용(부모는 403). 한 달치 정산의 금액·결과와 그 달 정산에 연결된 이체 내역(결제일 정산 SETTLEMENT, 부족분 갚기 SHORTFALL_PAYMENT(source: CARRYOVER 남은 돈 / DIRECT 직접 송금), 자동 정산 AUTO_DEDUCT)을 시간순으로. shortfallAmount 는 결제일에 부족했던 금액(나중에 갚았어도 그때의 부족액)입니다. 남의 정산이면 404.")
    @GetMapping("/settlements/{settlementId}")
    public ApiResponse<SettlementHistoryDtos.Detail> getDetail(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long settlementId) {
        return ApiResponse.onSuccess(SuccessStatus.SETTLEMENT_DETAIL_OK, settlementHistoryService.getDetail(personaId, settlementId));
    }

    @Operation(summary = "부족분 갚기 (남은 돈 / 직접 송금)",
            description = "자녀 전용(부모는 403). 부족분을 지난달까지 남은 돈(source=CARRYOVER)이나 직접 송금(source=DIRECT)으로 갚습니다. 나눠서 갚거나 두 방법을 섞어도 됩니다. amount 는 1원 이상이고 남은 부족분 이하여야 하고(넘으면 400), CARRYOVER 는 지난달까지 남은 돈 이하여야 합니다(넘으면 400). 결제일이 지나 부족이 남은 정산(SHORTFALL·PARTIAL)만 갚을 수 있고, 정산 전이거나 이미 끝난 정산은 갚을 부족분이 없어 409. CARRYOVER 는 계좌 잔액과 남은 돈이 같이 줄고(이번 달 쓸 수 있는 돈은 그대로), DIRECT 는 시연에서 갚은 기록만 남깁니다. 전부 갚으면 COMPLETED(SELF_RESOLVED), 일부만 갚으면 PARTIAL.")
    @PostMapping("/settlements/{settlementId}/shortfall-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShortfallPaymentDtos.Response> payShortfall(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long settlementId,
            @Valid @RequestBody ShortfallPaymentDtos.Request request) {
        return ApiResponse.onSuccess(SuccessStatus.SHORTFALL_PAID,
                shortfallPaymentService.pay(personaId, settlementId, request.source(), request.amount()));
    }
}
