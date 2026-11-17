package com.cheotdanchu.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CertificateDtos {

    private CertificateDtos() {}

    /** 발급 요건 두 가지(만 19세, 검증된 결제일 정산 12회)의 진행 상황 */
    public record EligibilityResponse(
            boolean ageReached,
            long birthdayDDay,          // 만 19세 생일까지 남은 날 (이미 지났으면 0)
            int settlementCount,        // 검증된 결제일 정산 횟수 (요건 횟수를 넘으면 요건 횟수로 맞춤)
            int settlementRequired,
            boolean canIssue,
            LocalDate availableFrom) {} // 두 요건이 모두 채워지는 날 (추정). 이미 발급할 수 있으면 null

    /** 가장 최근에 발급한 확인서와 만료 여부 */
    public record LatestResponse(
            Long certificateId,
            int fullPaidMonths,
            int totalMonths,
            int maxStreak,
            double avgMarginRate,
            int selfResolvedCount,
            int shortfallCount,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt,
            boolean expired) {}

    /** 본인 인증은 시연에서 화면 단계로만 두고, 서버는 끝났는지만 받는다 */
    public record IssueRequest(@NotNull Boolean identityVerified) {}

    public record IssueResponse(Long certificateId, LocalDateTime issuedAt, LocalDateTime expiresAt) {}
}
