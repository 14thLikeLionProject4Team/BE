package com.cheotdanchu.service;

import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Settlement.Result;
import com.cheotdanchu.service.RecordsCalculator.Month;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cheotdanchu.entity.Settlement.Result.AUTO_DEDUCTED;
import static com.cheotdanchu.entity.Settlement.Result.FULL_PAID;
import static com.cheotdanchu.entity.Settlement.Result.SELF_RESOLVED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 기록 지표 계산 (DB 없이 월별 결과만 넣어서 확인) */
class RecordsCalculatorTest {

    private static Month month(Result result, long balanceAtDue, long billed) {
        return new Month(result, balanceAtDue, billed);
    }

    // 루피(가족 A)의 시드: 2025-11 ~ 2026-09, 11개월. 2026-03 한 달만 부족해서 다음 용돈에서 자동 정산됐다
    // (그래서 2026-04의 결제일 시점 잔액은 용돈 70,000원에서 전달 부족분 5,200원을 뺀 64,800원이다)
    private static final List<Month> FAMILY_A = List.of(
            month(FULL_PAID, 60000, 52900),
            month(FULL_PAID, 60000, 56800),
            month(FULL_PAID, 63000, 55200),
            month(FULL_PAID, 60000, 55100),
            month(AUTO_DEDUCTED, 70000, 75200),
            month(FULL_PAID, 64800, 58500),
            month(FULL_PAID, 70000, 64100),
            month(FULL_PAID, 70000, 66400),
            month(FULL_PAID, 73500, 65400),
            month(FULL_PAID, 73000, 68500),
            month(FULL_PAID, 70000, 61500));

    // 명세 예시: 결제일 완납 10/11개월, 최장 연속 완납 6개월, 결제일 여유율 평균 7.5%, 부족분 스스로 해소 0/1건
    @Test
    void 가족A의_시드는_명세_예시와_같은_지표가_나온다() {
        RecordsSummaryResponse r = RecordsCalculator.summarize(FAMILY_A);

        assertEquals(10, r.fullPaidMonths());
        assertEquals(11, r.totalMonths());
        assertEquals(6, r.maxStreak());
        assertEquals(7.5, r.avgMarginRate());
        assertEquals(0, r.selfResolvedCount());
        assertEquals(1, r.shortfallCount());
    }

    @Test
    void 부족한_달은_여유율이_음수로_평균에_들어간다() {
        // (100,000원 중 80,000원 사용 → +20%)와 (50,000원인데 60,000원 사용 → -20%)의 평균은 0%
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of(
                month(FULL_PAID, 100000, 80000),
                month(SELF_RESOLVED, 50000, 60000)));

        assertEquals(0.0, r.avgMarginRate());
    }

    @Test
    void 연속_완납은_부족한_달에서_끊기고_해소_방식과_상관없다() {
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of(
                month(FULL_PAID, 100, 50), month(FULL_PAID, 100, 50), month(FULL_PAID, 100, 50),
                month(SELF_RESOLVED, 100, 120),
                month(FULL_PAID, 100, 50), month(FULL_PAID, 100, 50)));

        assertEquals(3, r.maxStreak());
        assertEquals(5, r.fullPaidMonths());
    }

    @Test
    void 스스로_해소와_자동_정산을_나누어_센다() {
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of(
                month(SELF_RESOLVED, 100, 120),
                month(SELF_RESOLVED, 100, 130),
                month(AUTO_DEDUCTED, 100, 140)));

        assertEquals(3, r.shortfallCount());
        assertEquals(2, r.selfResolvedCount());
        assertEquals(0, r.fullPaidMonths());
        assertEquals(0, r.maxStreak());
    }

    @Test
    void 정산이_없으면_모두_0이다() {
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of());

        assertEquals(new RecordsSummaryResponse(0, 0, 0, 0.0, 0, 0), r);
    }

    @Test
    void 결제일_시점_잔액이_0인_달은_여유율_평균에서_빼고_개월_수에는_센다() {
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of(
                month(FULL_PAID, 100, 90),
                month(AUTO_DEDUCTED, 0, 5000)));

        assertEquals(2, r.totalMonths());
        assertEquals(10.0, r.avgMarginRate());
    }

    @Test
    void 여유율_평균은_소수_첫째_자리로_반올림한다() {
        // 1/3 = 33.333...% → 33.3
        RecordsSummaryResponse r = RecordsCalculator.summarize(List.of(month(FULL_PAID, 300, 200)));

        assertEquals(33.3, r.avgMarginRate());
    }
}
