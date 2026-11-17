package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.PaymentDtos;
import com.cheotdanchu.service.ChildPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ChildPaymentController {

    private final ChildPaymentService childPaymentService;

    @Operation(summary = "결제 요약 조회",
            description = "자녀 전용(부모는 403). 이번 정산의 청구 예정액(취소 합산), 결제 건수(취소 제외), 결제일. 진행 중인 정산이 없으면 에러가 아니라 data 가 null 입니다.")
    @GetMapping("/child/payments/summary")
    public ApiResponse<PaymentDtos.SummaryResponse> getSummary(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.PAYMENT_SUMMARY_OK, childPaymentService.getSummary(personaId));
    }

    @Operation(summary = "결제 목록 조회",
            description = "자녀 전용(부모는 403). 이번 정산 기간의 카드 결제를 가맹점명·카테고리·금액과 함께 보여줍니다. sort=date(날짜순, 기본)|amount(큰 금액순), from·to 는 정산 기간 안(아니면 400), size 는 기본 20(최대 100), cursor 는 이전 응답의 nextCursor(정렬을 바꾸면 처음부터). 취소는 isCanceled=true 이고 금액이 마이너스입니다. isHighAmount 는 위험 결제(HIGH_AMOUNT)로 표시된 결제이고 기준 금액은 아직 정해지지 않았습니다. 진행 중인 정산이 없으면 빈 목록입니다.")
    @GetMapping("/child/payments")
    public ApiResponse<PaymentDtos.PaymentListResponse> getPayments(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.onSuccess(SuccessStatus.PAYMENT_LIST_OK, childPaymentService.getPayments(personaId, sort, from, to, size, cursor));
    }

    @Operation(summary = "카테고리 비율 조회",
            description = "자녀 전용(부모는 403). 선택한 기간(기본: 이번 정산 기간 전체)의 카테고리별 합계와 비중(%, 소수 첫째 자리). 취소는 합산하고 합계가 0 이하인 카테고리는 뺍니다. from·to 는 정산 기간 안이어야 하고(아니면 400), 진행 중인 정산이 없으면 빈 목록입니다.")
    @GetMapping("/child/payments/category-ratio")
    public ApiResponse<PaymentDtos.CategoryRatioResponse> getCategoryRatio(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.onSuccess(SuccessStatus.CATEGORY_RATIO_OK, childPaymentService.getCategoryRatio(personaId, from, to));
    }
}
