package com.cheotdanchu.service;

import com.cheotdanchu.entity.Settlement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertificateReportCalculatorTest {

    private static final CertificateReport.Identity IDENTITY =
            new CertificateReport.Identity("CD-2026-1019-0001", "뽀로로", 2007, LocalDate.of(2026, 10, 19), LocalDate.of(2027, 1, 17));

    /** 종료일이 그 달 25일인 끝난 정산 하나. resolveDays 가 null 이 아니면 결제일(25일 18시) 그 날 수 뒤에 해소됨 */
    private static Settlement settlement(YearMonth month, Settlement.Result result, int billed, int balance, Integer resolveDays) {
        Settlement s = BeanUtils.instantiateClass(Settlement.class);
        LocalDate end = month.atDay(25);
        ReflectionTestUtils.setField(s, "periodEnd", end);
        ReflectionTestUtils.setField(s, "dueAt", end.atTime(18, 0));
        ReflectionTestUtils.setField(s, "result", result);
        ReflectionTestUtils.setField(s, "billedAmount", billed);
        ReflectionTestUtils.setField(s, "balanceAtDue", balance);
        if (resolveDays != null) ReflectionTestUtils.setField(s, "resolvedAt", LocalDateTime.of(end.plusDays(resolveDays), LocalTime.of(10, 0)));
        return s;
    }

    /** 2025-01 부터 count 개월. 3·7번째 달(0부터)은 부족해서 스스로 해소 */
    private static List<Settlement> months(int count) {
        List<Settlement> list = new ArrayList<>();
        YearMonth month = YearMonth.of(2025, 1);
        for (int i = 0; i < count; i++) {
            boolean shortfall = i == 3 || i == 7;
            list.add(settlement(month.plusMonths(i), shortfall ? Settlement.Result.SELF_RESOLVED : Settlement.Result.FULL_PAID,
                    60_000, 80_000, shortfall ? (i == 3 ? 2 : 5) : null));
        }
        return list;
    }

    @Test
    void 관찰_기간과_횟수와_월_이용_규모를_계산한다() {
        CertificateReport r = CertificateReportCalculator.calculate(months(14), 15, IDENTITY);

        assertEquals(YearMonth.of(2025, 1), r.observedFrom());
        assertEquals(YearMonth.of(2026, 2), r.observedTo());
        assertEquals(14, r.verifiedCount());
        assertEquals(15, r.totalSettlements()); // 제외된 정산까지 센 전체
        assertEquals(60_000, r.averageBilled());
        assertEquals(IDENTITY, r.identity());
    }

    @Test
    void 최근_12개월은_마지막_12개월이다() {
        CertificateReport r = CertificateReportCalculator.calculate(months(14), 14, IDENTITY);

        assertEquals(YearMonth.of(2025, 3), r.recentFrom());
        assertEquals(14, r.all().months());
        assertEquals(12, r.recent().months());
    }

    @Test
    void 전체_기간과_최근_12개월의_완납_연속_부족_해소를_따로_센다() {
        CertificateReport r = CertificateReportCalculator.calculate(months(14), 14, IDENTITY);

        // 전체: 14개월 중 부족 2번(3, 7번째)이라 12개월 완납, 가장 긴 연속은 7번째 뒤 6개월(8~13번째)
        assertEquals(12, r.all().fullPaid());
        assertEquals(6, r.all().maxStreak());
        assertEquals(2, r.all().shortfallCount());
        assertEquals(2, r.all().selfResolved());
        // 최근 12개월(2~13번째): 부족 두 번이 모두 들어 있다
        assertEquals(10, r.recent().fullPaid());
        assertEquals(6, r.recent().maxStreak());
        assertEquals(2, r.recent().shortfallCount());
    }

    @Test
    void 부족이_최근_12개월_밖이면_최근은_해당_없음이다() {
        List<Settlement> list = new ArrayList<>(months(14));
        list.set(3, settlement(YearMonth.of(2025, 4), Settlement.Result.FULL_PAID, 60_000, 80_000, null)); // 오래된 부족을 완납으로
        list.set(1, settlement(YearMonth.of(2025, 2), Settlement.Result.SELF_RESOLVED, 60_000, 80_000, 3)); // 최근 12개월 밖(0,1번째)의 부족
        list.set(7, settlement(YearMonth.of(2025, 8), Settlement.Result.FULL_PAID, 60_000, 80_000, null));

        CertificateReport r = CertificateReportCalculator.calculate(list, 14, IDENTITY);

        assertEquals(1, r.all().shortfallCount());
        assertEquals(0, r.recent().shortfallCount());
        assertNull(r.recent().avgResolveDays());
        assertEquals(12, r.recent().fullPaid());
    }

    @Test
    void 평균_해소_기간은_스스로_해소한_부족분만_결제일부터_센다() {
        CertificateReport r = CertificateReportCalculator.calculate(months(14), 14, IDENTITY);

        assertEquals(3.5, r.all().avgResolveDays()); // (2일 + 5일) / 2건
    }

    @Test
    void 해소한_날짜가_없는_부족분은_평균_해소_기간에서_빠진다() {
        List<Settlement> list = new ArrayList<>(months(12));
        list.set(3, settlement(YearMonth.of(2025, 4), Settlement.Result.SELF_RESOLVED, 60_000, 80_000, null)); // 해소 날짜 모름

        CertificateReport r = CertificateReportCalculator.calculate(list, 12, IDENTITY);

        assertEquals(5.0, r.all().avgResolveDays());
        assertEquals(2, r.all().selfResolved()); // 해소 건수에는 세고 기간 평균에서만 뺀다
    }

    @Test
    void 결제일보다_일찍_해소했으면_0일로_센다() {
        List<Settlement> list = new ArrayList<>(months(12));
        list.set(3, settlement(YearMonth.of(2025, 4), Settlement.Result.SELF_RESOLVED, 60_000, 80_000, -2)); // 결제일 이틀 전에 해소
        list.set(7, settlement(YearMonth.of(2025, 8), Settlement.Result.SELF_RESOLVED, 60_000, 80_000, 4));

        CertificateReport r = CertificateReportCalculator.calculate(list, 12, IDENTITY);

        assertEquals(2.0, r.all().avgResolveDays()); // (0일 + 4일) / 2건, 음수가 평균을 깎지 않는다
    }

    @Test
    void 정산이_12개월일_때는_전체와_최근이_같다() {
        CertificateReport r = CertificateReportCalculator.calculate(months(12), 12, IDENTITY);

        assertEquals(r.observedFrom(), r.recentFrom());
        assertEquals(r.all(), r.recent());
    }

    @Test
    void 기록_지표_계산과_같은_숫자다() {
        List<Settlement> list = months(14);

        CertificateReport r = CertificateReportCalculator.calculate(list, 14, IDENTITY);

        assertEquals(RecordsCalculator.summarize(list.stream()
                .map(RecordsCalculator.Month::of).toList()).avgMarginRate(),
                r.all().avgMarginRate()); // 여유율은 명세대로 (결제일 잔액 - 청구액) / 결제일 잔액
    }

    @Test
    void 월_이용_규모는_구간으로만_나눈다() {
        assertEquals("5만 원 미만", CertificateReportCalculator.usageBand(49_999));
        assertEquals("5만~10만 원", CertificateReportCalculator.usageBand(50_000));
        assertEquals("5만~10만 원", CertificateReportCalculator.usageBand(99_999));
        assertEquals("10만~20만 원", CertificateReportCalculator.usageBand(100_000));
        assertEquals("20만 원 이상", CertificateReportCalculator.usageBand(200_000));
    }
}
