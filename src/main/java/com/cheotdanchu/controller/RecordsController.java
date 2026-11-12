package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.service.RecordsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecordsController {

    private final RecordsService recordsService;

    @Operation(summary = "기록 지표 조회",
            description = "자녀 전용. 확인서와 같은 지표 4개를 점수 없이 원값으로 반환합니다: 결제일 완납 개월(fullPaidMonths/totalMonths), 최장 연속 완납(maxStreak), 결제일 여유율 평균(avgMarginRate, %), 부족분 스스로 해소(selfResolvedCount)와 부족 발생(shortfallCount). 부모 지원·현금 정산으로 제외된 정산은 빠집니다.")
    @GetMapping("/records/summary")
    public ApiResponse<RecordsSummaryResponse> getSummary(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.RECORDS_SUMMARY_OK, recordsService.getSummary(personaId));
    }
}
