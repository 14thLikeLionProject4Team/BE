package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.ChildSpendingResponse;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.Transfer;
import com.cheotdanchu.repository.AccountRepository;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import com.cheotdanchu.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 부모 홈 "이번 정산 기간" 카드. 부모에게는 총액만 보여주고 부족 예상은 보여주지 않는다 (아이에게 먼저 간다) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildSpendingService {

    private static final int ADULT_AGE = 19;
    private static final String NOTICE = "결과는 결제일에 알려드려요";

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final FamilySettingRepository familySettingRepository;
    private final AllowanceChangeRepository allowanceChangeRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final SeedRepository seedRepository;

    /** 진행 중인 정산이 없으면 에러가 아니라 날짜는 null, 총액은 0 */
    public ChildSpendingResponse getChildSpending(Long personaId) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);

        Long familyId = parent.getFamily().getFamilyId();
        Member child = memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));

        LocalDate today = seedRepository.today();
        // 만 19세가 된 뒤의 기록은 본인만 본다 (부모에게는 성년 전환 카드만)
        if (!child.getBirthDate().plusYears(ADULT_AGE).isAfter(today)) {
            throw new GeneralException(ErrorStatus.ADULT_TRANSITIONED);
        }

        Optional<Settlement> current = settlementRepository.findCurrent(familyId, today);

        return new ChildSpendingResponse(
                current.map(s -> s.getDueAt().toLocalDate()).orElse(null),
                current.map(s -> ChronoUnit.DAYS.between(today, s.getDueAt().toLocalDate())).orElse(null),
                // 청구 예정 금액(현재 정산 조회의 billedAmount)과 같은 값
                current.map(s -> cardPaymentRepository.sumAmount(s.getSettlementId())).orElse(0L),
                allowance(familyId, child, current, today),
                NOTICE);
    }

    private ChildSpendingResponse.Allowance allowance(Long familyId, Member child, Optional<Settlement> current, LocalDate today) {
        FamilySetting setting = familySettingRepository.findById(familyId).orElse(null);
        if (setting == null) return null;

        // 이번 달 용돈 = 이번 달 용돈 보내는 날에 적용 중인 금액 (11월부터 바뀌는 용돈은 10월에는 해당 없음)
        YearMonth month = YearMonth.from(today);
        LocalDate allowanceDate = month.atDay(setting.getAllowanceDay());
        AllowanceChange change = allowanceChangeRepository
                .findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(familyId, allowanceDate)
                .orElse(null);
        if (change == null) return null;

        boolean sent = current.isPresent() && accountRepository.findByMemberId(child.getMemberId())
                .map(account -> transferRepository.existsByToAccountIdAndTypeAndTransferredAtGreaterThanEqualAndTransferredAtLessThan(
                        account.getAccountId(), Transfer.Type.ALLOWANCE,
                        current.get().getPeriodStart().atStartOfDay(),
                        current.get().getPeriodEnd().plusDays(1).atStartOfDay()))
                .orElse(false);

        return new ChildSpendingResponse.Allowance(month.toString(), change.getAmount(),
                setting.isAutoTransfer(), sent);
    }
}
