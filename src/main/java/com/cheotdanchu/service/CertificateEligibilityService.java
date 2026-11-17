package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 확인서 발급 요건: 만 19세가 되고, 검증된 결제일 정산이 12회 쌓일 것. 자녀 홈 칩과 발급 요건 조회가 같은 날짜를 쓴다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateEligibilityService {

    static final int REQUIRED_SETTLEMENTS = 12;

    private final MemberRepository memberRepository;
    private final RecordsService recordsService;
    private final SettlementRepository settlementRepository;
    private final SeedRepository seedRepository;

    /** 발급 요건 조회 */
    public CertificateDtos.EligibilityResponse getEligibility(Long personaId) {
        Member child = memberRepository.getChild(personaId);
        LocalDate today = seedRepository.today();
        int verified = recordsService.verifiedCount(child.getFamily().getFamilyId());

        boolean ageReached = child.isAdultOn(today);
        return new CertificateDtos.EligibilityResponse(
                ageReached,
                Math.max(ChronoUnit.DAYS.between(today, child.adultDay()), 0),
                Math.min(verified, REQUIRED_SETTLEMENTS),
                REQUIRED_SETTLEMENTS,
                ageReached && verified >= REQUIRED_SETTLEMENTS,
                availableFrom(child, today, verified));
    }

    /** 두 요건이 모두 채워지는 날 (자녀 홈 칩이 쓴다). 이미 발급할 수 있으면 null */
    public LocalDate availableFrom(Member child, LocalDate today) {
        return availableFrom(child, today, recordsService.verifiedCount(child.getFamily().getFamilyId()));
    }

    /**
     * 두 요건이 모두 채워지는 날(늦게 채워지는 쪽). 이미 발급할 수 있으면 null (과거의 정확한 날짜는 알 수 없다).
     * 정산 횟수는 아직 끝나지 않은 이번 정산부터 한 달에 하나씩 채워진다고 보고 추정한다(이번 정산이 이미 끝났으면 다음 달 정산부터).
     * 부족이 길어지면 실제로는 더 늦어질 수 있어서, 아직 못 채웠는데 추정 날짜가 지났거나 진행 중인 정산이 없으면 오늘로 둔다.
     */
    private LocalDate availableFrom(Member child, LocalDate today, int verified) {
        LocalDate adultDay = child.adultDay();
        int missing = REQUIRED_SETTLEMENTS - verified;
        if (missing <= 0) return adultDay.isAfter(today) ? adultDay : null;

        LocalDate twelfth = settlementRepository.findCurrent(child.getFamily().getFamilyId(), today)
                .map(s -> s.getDueAt().toLocalDate().plusMonths(s.getStatus() == Settlement.Status.COMPLETED ? missing : missing - 1))
                .orElse(today);
        LocalDate from = twelfth.isAfter(adultDay) ? twelfth : adultDay;
        return from.isAfter(today) ? from : today;
    }
}
