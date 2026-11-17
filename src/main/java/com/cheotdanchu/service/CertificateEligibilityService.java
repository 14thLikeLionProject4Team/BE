package com.cheotdanchu.service;

import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** 확인서 발급 요건: 만 19세가 되고, 검증된 결제일 정산이 12회 쌓일 것. 자녀 홈 칩과 발급 요건 조회가 같은 날짜를 쓴다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateEligibilityService {

    static final int ADULT_AGE = 19;
    static final int REQUIRED_SETTLEMENTS = 12;

    private final RecordsService recordsService;
    private final SettlementRepository settlementRepository;

    /**
     * 두 요건이 모두 채워지는 날(늦게 채워지는 쪽). 정산 횟수는 아직 끝나지 않은 이번 정산부터 한 달에 하나씩 채워진다고 보고 추정한다
     * (이번 정산이 이미 끝났으면 다음 달 정산부터). 부족이 길어지면 실제로는 더 늦어질 수 있다.
     * 진행 중인 정산이 없어서 채워질 날을 알 수 없으면 생일만 본다.
     */
    public LocalDate availableFrom(Member child, LocalDate today) {
        LocalDate birthday = child.getBirthDate().plusYears(ADULT_AGE);
        int missing = REQUIRED_SETTLEMENTS - recordsService.verifiedCount(child.getFamily().getFamilyId());
        if (missing <= 0) return birthday;

        Optional<LocalDate> twelfth = settlementRepository.findCurrent(child.getFamily().getFamilyId(), today)
                .map(s -> s.getDueAt().toLocalDate().plusMonths(s.getStatus() == Settlement.Status.COMPLETED ? missing : missing - 1));
        return twelfth.filter(birthday::isBefore).orElse(birthday);
    }
}
