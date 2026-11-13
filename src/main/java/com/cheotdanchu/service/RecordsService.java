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

        return RecordsCalculator.summarize(settlementRepository
                .findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(
                        member.getFamily().getFamilyId(), Settlement.Status.COMPLETED)
                .stream()
                .filter(s -> s.getResult() != null && s.getBalanceAtDue() != null && s.getBilledAmount() != null)
                .map(s -> new RecordsCalculator.Month(s.getResult(), s.getBalanceAtDue(), s.getBilledAmount()))
                .toList());
    }
}
