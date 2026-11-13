package com.cheotdanchu.service;

import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Settlement;

import java.util.List;

/** 기록 지표 계산. DB 없이 월별 정산 결과(오래된 순)만 받아서 계산한다 */
final class RecordsCalculator {

    private RecordsCalculator() {}

    /** 한 달 정산의 결과와, 결제일 시점 잔액·청구액 */
    record Month(Settlement.Result result, long balanceAtDue, long billed) {

        /** 결제일 여유율 = (결제일 시점 잔액 − 청구액) / 결제일 시점 잔액. 부족한 달은 음수 */
        double marginRate() {
            return (double) (balanceAtDue - billed) / balanceAtDue;
        }
    }

    static RecordsSummaryResponse summarize(List<Month> months) {
        int fullPaid = 0;
        int selfResolved = 0;
        int shortfall = 0;
        int streak = 0;
        int maxStreak = 0;
        double marginSum = 0;
        int marginCount = 0;

        for (Month m : months) {
            if (m.result() == Settlement.Result.FULL_PAID) {
                fullPaid++;
                maxStreak = Math.max(maxStreak, ++streak);
            } else {
                streak = 0; // 부족이 났던 달은 해소 방식과 상관없이 연속 완납이 끊긴다
                shortfall++;
                if (m.result() == Settlement.Result.SELF_RESOLVED) selfResolved++;
            }
            if (m.balanceAtDue() > 0) { // 결제일 시점 잔액이 0이면 비율을 구할 수 없다
                marginSum += m.marginRate();
                marginCount++;
            }
        }

        double avgMarginRate = marginCount == 0 ? 0 : Math.round(marginSum / marginCount * 1000) / 10.0;
        return new RecordsSummaryResponse(fullPaid, months.size(), maxStreak, avgMarginRate, selfResolved, shortfall);
    }
}
