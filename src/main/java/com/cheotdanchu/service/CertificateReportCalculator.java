package com.cheotdanchu.service;

import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Settlement;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalDouble;

/** 검증된 정산(오래된 순)에서 확인서에 들어갈 전체 기간·최근 12개월 지표를 계산한다. DB 없이 계산만 한다 */
final class CertificateReportCalculator {

    /** 최근 지표를 보는 개월 수 */
    static final int RECENT_MONTHS = 12;

    private CertificateReportCalculator() {}

    /**
     * @param verified          검증된 정산(오래된 순, 1개 이상). 확인서는 검증된 정산이 12회 이상일 때만 발급한다
     * @param totalSettlements  끝난 정산 전체 수 (제외된 것 포함)
     * @param identity          이름·출생연도·발급번호·발급일·유효기한 (지표는 이 메서드가 채운다)
     */
    static CertificateReport calculate(List<Settlement> verified, long totalSettlements, CertificateReport.Identity identity) {
        int n = verified.size();
        List<Settlement> recent = verified.subList(Math.max(n - RECENT_MONTHS, 0), n);

        return new CertificateReport(
                identity,
                YearMonth.from(verified.get(0).getPeriodEnd()),
                YearMonth.from(verified.get(n - 1).getPeriodEnd()),
                n,
                totalSettlements,
                Math.round(verified.stream().mapToInt(Settlement::getBilledAmount).average().orElse(0)),
                YearMonth.from(recent.get(0).getPeriodEnd()),
                stats(verified),
                stats(recent));
    }

    private static CertificateReport.Stats stats(List<Settlement> months) {
        // 기록 지표 조회·미리보기와 같은 계산이라 같은 숫자가 나온다
        RecordsSummaryResponse r = RecordsCalculator.summarize(months.stream()
                .map(RecordsCalculator.Month::of)
                .toList());
        return new CertificateReport.Stats(r.totalMonths(), r.fullPaidMonths(), r.maxStreak(), r.avgMarginRate(),
                r.shortfallCount(), r.selfResolvedCount(), averageResolveDays(months));
    }

    /** 스스로 해소한 부족분이 결제일부터 해소될 때까지 걸린 날 수의 평균. 해소한 날짜가 없거나 해소한 건이 없으면 null */
    private static Double averageResolveDays(List<Settlement> months) {
        OptionalDouble average = months.stream()
                .filter(s -> s.getResult() == Settlement.Result.SELF_RESOLVED && s.getResolvedAt() != null)
                .mapToLong(s -> Math.max(ChronoUnit.DAYS.between(s.getDueAt().toLocalDate(), s.getResolvedAt().toLocalDate()), 0)) // 결제일 전에 해소했으면 0일
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    /** 월 평균 청구액이 속한 구간 이름. 금액은 확인서에 구간으로만 적는다 */
    static String usageBand(long averageBilled) {
        if (averageBilled < 50_000) return "5만 원 미만";
        if (averageBilled < 100_000) return "5만~10만 원";
        if (averageBilled < 200_000) return "10만~20만 원";
        return "20만 원 이상";
    }
}
