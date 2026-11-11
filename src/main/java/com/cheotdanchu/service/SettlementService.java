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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    /** 부족 예상 점검은 결제일 D-6부터 결제일까지 보여준다 */
    private static final int CHECK_START_D_DAY = 6;

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final AccountRepository accountRepository;
    private final SeedRepository seedRepository;

    /** 현재 정산 조회와 부족 예상 점검이 함께 쓰는 계산 결과 */
    private record Snapshot(Settlement settlement, long dDay, long billed, long count, long available, long carryover) {}

    public CurrentSettlementResponse getCurrent(Long personaId) {
        Snapshot s = snapshot(personaId);
        // 부족 예상액은 지난달까지 남은 돈을 포함하지 않는다
        long gap = s.billed() - s.available();

        return new CurrentSettlementResponse(
                s.settlement().getSettlementId(),
                s.settlement().getPeriodStart(),
                s.settlement().getPeriodEnd(),
                s.settlement().getDueAt(),
                s.dDay(),
                s.settlement().getStatus(),
                s.billed(),
                s.count(),
                s.available(),
                s.carryover(),
                Math.max(gap, 0),
                Math.max(-gap, 0),
                true); // 시연: 카드사 연동은 항상 켜짐 (연동 방식은 명세상 미정)
    }

    public ShortfallCheckResponse getCheck(Long personaId) {
        Snapshot s = snapshot(personaId);
        return check(s.settlement().getDueAt().toLocalDate(), s.dDay(), s.billed(), s.available(), s.carryover());
    }

    /**
     * 부족 예상 점검 카드. 결제일 D-6부터 결제일까지, 이번 달 쓸 수 있는 돈이 청구 예정액보다 적을 때만 보여준다.
     * 지난달까지 남은 돈으로 메울 수 있어도 보여주고, 안내에 남은 돈으로 갚을 수 있다고 알린다.
     */
    static ShortfallCheckResponse check(LocalDate dueDate, long dDay, long billed, long available, long carryover) {
        long gap = billed - available;
        boolean visible = gap > 0 && dDay >= 0 && dDay <= CHECK_START_D_DAY;
        if (!visible) {
            return new ShortfallCheckResponse(false, dueDate, dDay, billed, available, carryover, 0, null, List.of());
        }

        List<String> guides = new ArrayList<>();
        if (carryover >= gap) guides.add("지난달까지 남은 돈 " + won(carryover) + "원으로 갚을 수 있어요");
        else if (carryover > 0) guides.add("지난달까지 남은 돈 " + won(carryover) + "원으로 일부 갚을 수 있어요");
        guides.add("다음 용돈 전까지 직접 보내도 돼요");
        guides.add("다른 사람에게 빌리지 마세요");

        return new ShortfallCheckResponse(true, dueDate, dDay, billed, available, carryover, gap,
                "지금 속도면 결제일에 " + won(gap) + "원이 모자랄 것 같아요", guides);
    }

    private Snapshot snapshot(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
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
        // 이번 달 쓸 수 있는 돈 = 계좌 전체 잔액 − 지난달까지 남은 돈 (용돈 + 미션 보상에서 부족분 송금 등으로 나간 돈을 뺀 값)
        long available = account.getBalance() - account.getCarryoverBalance();

        return new Snapshot(settlement,
                ChronoUnit.DAYS.between(today, settlement.getDueAt().toLocalDate()),
                billed, count, available, account.getCarryoverBalance());
    }

    private static String won(long amount) {
        return String.format(Locale.KOREA, "%,d", amount);
    }
}
