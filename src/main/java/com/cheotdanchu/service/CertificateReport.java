package com.cheotdanchu.service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 확인서 PDF 에 들어가는 값. 검증된 정산(오래된 순)에서 계산해 두고 PDF 는 이 값을 글자로만 옮긴다.
 * 전체 기간과 최근 12개월 지표는 같은 계산(RecordsCalculator)을 써서 기록 지표 조회·미리보기와 숫자가 같다.
 */
record CertificateReport(
        Identity identity,
        YearMonth observedFrom,        // 관찰 기간: 검증된 첫 정산·마지막 정산이 끝나는 달
        YearMonth observedTo,
        int verifiedCount,             // 검증된 정산 수
        long totalSettlements,         // 끝난 정산 전체 수 (제외된 것 포함)
        long averageBilled,            // 검증된 정산의 월 평균 청구액
        YearMonth recentFrom,          // 최근 12개월 구간 (끝은 observedTo)
        Stats all,
        Stats recent) {

    /** 발급 정보 */
    record Identity(String number, String name, int birthYear, LocalDate issuedOn, LocalDate validUntil) {}

    /** 한 기간의 지표 */
    record Stats(
            int months,
            int fullPaid,
            int maxStreak,
            double avgMarginRate,      // %
            int shortfallCount,
            int selfResolved,
            Double avgResolveDays) {   // 스스로 해소한 부족분이 없으면 null
    }
}
