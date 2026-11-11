package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.dto.ShortfallCheckResponse;
import com.cheotdanchu.entity.Account;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.AccountRepository;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    /** 부족 예상 점검은 결제일 D-6부터 결제일까지 보여준다 */
    private static final int CHECK_START_D_DAY = 6;

    /** 결제일 전(정산 예정)과 결제일 도래(정산 필요)에서만 보여준다. 정산이 시작된 뒤의 부족분은 부족분 결제 쪽에서 다룬다 */
    private static final Set<Settlement.Status> CHECK_STATUSES =
            EnumSet.of(Settlement.Status.SCHEDULED, Settlement.Status.REQUIRED);

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final AccountRepository accountRepository;
    private final SeedRepository seedRepository;

    /**
     * 현재 정산 조회와 부족 예상 점검이 함께 쓰는 계산 결과. 서비스 안의 snapshot() 한 곳에서만 만든다
     * (같은 타입 숫자가 여러 개라 곳곳에서 만들면 순서가 바뀌어도 컴파일되기 때문).
     */
    record Snapshot(Settlement settlement, long dDay, long billed, long available, long carryover) {

        /** 부족 예상액(음수면 남는 돈) = 청구 예정액 − 이번 달 쓸 수 있는 돈. 지난달까지 남은 돈은 포함하지 않는다 */
        long gap() {
            return billed - available;
        }

        LocalDate dueDate() {
            return settlement.getDueAt().toLocalDate();
        }
    }

    /** 진행 중인 정산이 없는 가족(성년 전환 후 등)이면 null. 에러가 아니라 "정산 없음"이다 */
    public CurrentSettlementResponse getCurrent(Long personaId) {
        Member member = child(personaId);
        LocalDate today = seedRepository.today();
        Optional<Settlement> current = findCurrent(member, today);
        if (current.isEmpty()) return null;

        Snapshot s = snapshot(current.get(), account(personaId), today);
        // 결제 건수는 현재 정산 조회에서만 쓰므로 여기서만 센다 (점검 조회는 건수를 쓰지 않음)
        long count = cardPaymentRepository.countBySettlementIdAndCanceledFalse(s.settlement().getSettlementId());

        return new CurrentSettlementResponse(
                s.settlement().getSettlementId(),
                s.settlement().getPeriodStart(),
                s.settlement().getPeriodEnd(),
                s.settlement().getDueAt(),
                s.dDay(),
                s.settlement().getStatus(),
                s.billed(),
                count,
                s.available(),
                s.carryover(),
                Math.max(s.gap(), 0),
                Math.max(-s.gap(), 0),
                true); // 시연: 카드사 연동은 항상 켜짐 (연동 방식은 명세상 미정)
    }

    /** 진행 중인 정산이 없으면 에러가 아니라 visible=false (날짜는 null, 청구액은 0) */
    public ShortfallCheckResponse getCheck(Long personaId) {
        Member member = child(personaId);
        Account account = account(personaId);
        LocalDate today = seedRepository.today();

        return findCurrent(member, today)
                .map(settlement -> check(snapshot(settlement, account, today)))
                .orElseGet(() -> noSettlement(account));
    }

    /**
     * 부족 예상 점검 카드. 정산 전(SCHEDULED·REQUIRED)이고 결제일 D-6부터 결제일까지,
     * 이번 달 쓸 수 있는 돈이 청구 예정액보다 적을 때만 보여준다.
     * 지난달까지 남은 돈으로 메울 수 있어도 보여주고, 안내에 남은 돈으로 갚을 수 있다고 알린다.
     */
    static ShortfallCheckResponse check(Snapshot s) {
        long gap = s.gap();
        boolean visible = CHECK_STATUSES.contains(s.settlement().getStatus())
                && gap > 0 && s.dDay() >= 0 && s.dDay() <= CHECK_START_D_DAY;
        if (!visible) {
            return new ShortfallCheckResponse(false, s.dueDate(), s.dDay(), s.billed(), s.available(), s.carryover(),
                    0, null, List.of());
        }
        return new ShortfallCheckResponse(true, s.dueDate(), s.dDay(), s.billed(), s.available(), s.carryover(),
                gap, ShortfallCheckCopy.message(gap), ShortfallCheckCopy.guides(gap, s.carryover()));
    }

    private static ShortfallCheckResponse noSettlement(Account account) {
        return new ShortfallCheckResponse(false, null, null, 0, availableOf(account), account.getCarryoverBalance(),
                0, null, List.of());
    }

    private Member child(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.FORBIDDEN_ROLE);
        return member;
    }

    private Account account(Long personaId) {
        return accountRepository.findByMemberId(personaId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ACCOUNT_NOT_FOUND));
    }

    private Optional<Settlement> findCurrent(Member member, LocalDate today) {
        return settlementRepository.findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                member.getFamily().getFamilyId(), today, today);
    }

    private Snapshot snapshot(Settlement settlement, Account account, LocalDate today) {
        return new Snapshot(settlement,
                ChronoUnit.DAYS.between(today, settlement.getDueAt().toLocalDate()),
                cardPaymentRepository.sumAmount(settlement.getSettlementId()),
                availableOf(account),
                account.getCarryoverBalance());
    }

    /** 이번 달 쓸 수 있는 돈 = 계좌 전체 잔액 − 지난달까지 남은 돈 (용돈 + 미션 보상에서 부족분 송금 등으로 나간 돈을 뺀 값) */
    private static long availableOf(Account account) {
        return account.getBalance() - account.getCarryoverBalance();
    }
}
