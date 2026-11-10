package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.entity.Account;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.Transfer;
import com.cheotdanchu.repository.AccountRepository;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import com.cheotdanchu.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    /** 이번 달 쓸 수 있는 돈 = 이번 달 용돈 + 미션 보상 (계좌 전체 잔액 아님) */
    private static final Set<Transfer.Type> AVAILABLE_TYPES =
            EnumSet.of(Transfer.Type.ALLOWANCE, Transfer.Type.MISSION_REWARD);

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final SeedRepository seedRepository;

    public CurrentSettlementResponse getCurrent(Long personaId) {
        Member member = memberRepository.findById(personaId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.FORBIDDEN_ROLE);

        LocalDate today = seedRepository.today();
        Settlement settlement = settlementRepository
                .findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        member.getFamily().getFamilyId(), today, today)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SETTLEMENT_NOT_FOUND));

        Account account = accountRepository.findByMemberId(personaId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ACCOUNT_NOT_FOUND));

        long billed = cardPaymentRepository.sumAmount(settlement.getSettlementId());
        long count = cardPaymentRepository.countBySettlementIdAndCanceledFalse(settlement.getSettlementId());
        long available = transferRepository.sumReceived(
                account.getAccountId(), AVAILABLE_TYPES,
                settlement.getPeriodStart().atStartOfDay(),
                settlement.getPeriodEnd().plusDays(1).atStartOfDay());

        // 부족 예상액은 지난달까지 남은 돈을 포함하지 않는다
        long gap = billed - available;

        return new CurrentSettlementResponse(
                settlement.getSettlementId(),
                settlement.getPeriodStart(),
                settlement.getPeriodEnd(),
                settlement.getDueAt(),
                ChronoUnit.DAYS.between(today, settlement.getDueAt().toLocalDate()),
                settlement.getStatus(),
                billed,
                count,
                available,
                account.getCarryoverBalance(),
                Math.max(gap, 0),
                Math.max(-gap, 0),
                true); // 시연: 카드사 연동은 항상 켜짐 (연동 방식은 명세상 미정)
    }
}
