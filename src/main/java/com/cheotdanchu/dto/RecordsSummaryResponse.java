package com.cheotdanchu.dto;

/** 확인서와 같은 지표 4개를 점수 없이 원값으로 내려준다 */
public record RecordsSummaryResponse(
        int fullPaidMonths,     // 결제일 완납 개월 수
        int totalMonths,        // 대상 개월 수 (끝난 정산 중 제외되지 않은 것)
        int maxStreak,          // 최장 연속 완납 개월 수
        double avgMarginRate,   // 결제일 여유율 평균 (%, 소수 첫째 자리)
        int selfResolvedCount,  // 부족분 스스로 해소 건수
        int shortfallCount      // 부족 발생 건수
) {}
