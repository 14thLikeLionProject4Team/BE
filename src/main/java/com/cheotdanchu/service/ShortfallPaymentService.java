package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.ShortfallPaymentDtos;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

/** 부족분 갚기 (자녀 전용): 지난달까지 남은 돈이나 직접 송금으로 부족분을 나눠서 또는 한 번에 갚는다 */
@Service
@RequiredArgsConstructor
public class ShortfallPaymentService {

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final SeedRepository seedRepository;

    /**
     * 결제일이 지나 부족이 남은 정산(SHORTFALL·PARTIAL)의 부족분을 갚는다. 그 밖의 정산(정산 전·끝난 정산)은 갚을 부족분이 없어서 409 다.
     * 갚을 수 있는 금액은 남은 부족분 이하이고, 지난달까지 남은 돈으로 갚을 때는 그 돈 이하다.
     * - 지난달까지 남은 돈(CARRYOVER): 계좌 잔액과 남은 돈이 같이 줄어든다 (이번 달 쓸 수 있는 돈은 그대로)
     * - 직접 송금(DIRECT): 시연에서는 실제 이체 없이 갚은 기록만 남긴다 (계좌 잔액은 그대로)
     * 전부 갚으면 스스로 해소(COMPLETED·SELF_RESOLVED)로, 일부만 갚으면 일부 정산(PARTIAL)을 유지한다.
     * 같은 정산·같은 계좌의 동시 요청(연타)은 행 잠금으로 한 줄로 처리하고, 잠금이 풀린 뒤 앞 요청이 저장한 것이 보이도록
     * READ_COMMITTED 로 읽는다 (기본값은 트랜잭션 처음 읽은 시점의 모습만 본다).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShortfallPaymentDtos.Response pay(Long personaId, Long settlementId, Transfer.Source source, int amount) {
        Member child = memberRepository.getChild(personaId);
        Long familyId = child.getFamily().getFamilyId();

        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .filter(s -> s.getFamilyId().equals(familyId))
                .orElseThrow(() -> new GeneralException(ErrorStatus.SETTLEMENT_NOT_FOUND));
        Account account = accountRepository.findByMemberIdForUpdate(child.getMemberId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.ACCOUNT_NOT_FOUND));

        long remaining = remainingShortfall(settlement);
        if (remaining <= 0) throw new GeneralException(ErrorStatus.NO_SHORTFALL, noShortfallMessage(settlement));
        if (amount > remaining) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, String.format(Locale.KOREA, "amount: 남은 부족분(%,d원)을 넘을 수 없어요", remaining));
        }
        if (source == Transfer.Source.CARRYOVER && amount > account.getCarryoverBalance()) {
            throw new GeneralException(ErrorStatus.INSUFFICIENT_CARRYOVER,
                    String.format(Locale.KOREA, "지난달까지 남은 돈(%,d원)보다 많이 갚을 수 없어요", account.getCarryoverBalance()));
        }

        Long parentAccountId = memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.PARENT)
                .flatMap(parent -> accountRepository.findByMemberId(parent.getMemberId()))
                .map(Account::getAccountId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ACCOUNT_NOT_FOUND));
        // 이체·정산 시각은 데모 기준일 + 지금 시각 (실제 날짜로 남기면 다른 화면의 기간 계산과 어긋난다)
        LocalDateTime now = seedRepository.today().atTime(LocalTime.now().withNano(0));

        Transfer transfer = transferRepository.save(
                Transfer.shortfallPayment(account.getAccountId(), parentAccountId, amount, settlement.getSettlementId(), source, now));
        if (source == Transfer.Source.CARRYOVER) account.payFromCarryover(amount);

        long left = remaining - amount;
        if (left == 0) settlement.resolveBySelf(now);
        else settlement.markPartial();
        return new ShortfallPaymentDtos.Response(transfer.getTransferId(), settlement.getSettlementId(), source, amount,
                account.getCarryoverBalance(), left, settlement.getStatus(), settlement.getResult());
    }

    /** 아직 갚을 부족분 (계산은 정산 상세와 같은 Settlement.remainingShortfall) */
    private long remainingShortfall(Settlement settlement) {
        Long id = settlement.getSettlementId();
        long billed = settlement.getBilledAmount() != null ? settlement.getBilledAmount() : cardPaymentRepository.sumAmount(id);
        return settlement.remainingShortfall(billed,
                transferRepository.sumBySettlementAndType(id, Transfer.Type.SETTLEMENT),
                transferRepository.sumBySettlementAndType(id, Transfer.Type.SHORTFALL_PAYMENT));
    }

    /** 갚을 부족분이 없을 때의 안내. 결제일 전에는 부족 예상이 있어도 결제일이 지난 뒤에 갚을 수 있다고 알려준다 */
    private static String noShortfallMessage(Settlement settlement) {
        boolean beforeDue = settlement.getStatus() == Settlement.Status.SCHEDULED || settlement.getStatus() == Settlement.Status.REQUIRED;
        return beforeDue ? "결제일이 지난 뒤에 갚을 수 있어요" : ErrorStatus.NO_SHORTFALL.getMessage();
    }
}
