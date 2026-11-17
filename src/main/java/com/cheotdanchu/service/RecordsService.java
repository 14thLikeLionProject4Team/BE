package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordsService {

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;

    /**
     * 끝난 정산(COMPLETED) 중 제외되지 않은 것으로 지표를 계산한다.
     * 제외: 부모 지원·현금 정산(is_excluded). 미션 보상은 정산 금액에 들어가지 않아 따로 뺄 것이 없다.
     * 결과나 금액이 비어 있는 정산은 검증되지 않은 기록이라 계산에서 뺀다 (부족으로 잘못 세거나 에러가 나지 않게).
     */
    public RecordsSummaryResponse getSummary(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.CHILD_ONLY);

        return RecordsCalculator.summarize(verified(member.getFamily().getFamilyId()).stream()
                .map(s -> new RecordsCalculator.Month(s.getResult(), s.getBalanceAtDue(), s.getBilledAmount()))
                .toList());
    }

    /**
     * 지금까지 이어진 연속 완납 개월 수: 가장 최근 끝난 정산(제외된 것은 건너뜀)부터 거슬러 올라가며 완납이 끊길 때까지 센다.
     * 부모 홈 칭찬 칩이 쓰던 계산 그대로라 결과가 비어 있는 정산은 건너뛰지 않고 연속을 끊는다.
     */
    public int currentStreak(Long familyId) {
        List<Settlement> months = settlementRepository.findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(
                familyId, Settlement.Status.COMPLETED);
        int streak = 0;
        for (int i = months.size() - 1; i >= 0 && months.get(i).getResult() == Settlement.Result.FULL_PAID; i--) streak++;
        return streak;
    }

    /** 검증된 결제일 정산 횟수 (확인서 발급 요건) */
    public int verifiedCount(Long familyId) {
        return verified(familyId).size();
    }

    /** 끝난 정산 중 제외되지 않고 결과·금액이 채워진 것, 오래된 순 */
    private List<Settlement> verified(Long familyId) {
        return settlementRepository.findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(familyId, Settlement.Status.COMPLETED)
                .stream()
                .filter(s -> s.getResult() != null && s.getBalanceAtDue() != null && s.getBilledAmount() != null)
                .toList();
    }
}
