package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;

/**
 * 용돈 변경을 이력에 남기고 아이에게 알리는 공통 저장 로직.
 * AI 제안 "다음 달부터 적용"과 (앞으로 만들) 용돈 변경 저장이 같이 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AllowanceChangeService {

    private final AllowanceChangeRepository allowanceChangeRepository;
    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final SeedRepository seedRepository;

    /** 지금 정하면 적용되는 날. 정산 기간 중간에는 바꾸지 않으므로 다음 달 1일이다 */
    public LocalDate nextEffectiveDate() {
        return YearMonth.from(seedRepository.today()).plusMonths(1).atDay(1);
    }

    /** 그날 적용 중인 월 용돈 */
    public Optional<Integer> amountOn(Long familyId, LocalDate date) {
        return allowanceChangeRepository
                .findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(familyId, date)
                .map(AllowanceChange::getAmount);
    }

    @Transactional
    public AllowanceChange record(Member parent, int amount, AllowanceChange.Reason reason, AllowanceChange.Source source) {
        LocalDate today = seedRepository.today();
        Long familyId = parent.getFamily().getFamilyId();
        Integer prevAmount = amountOn(familyId, today).orElse(null);
        LocalDate effectiveFrom = nextEffectiveDate();
        // 이력·알림 시각은 데모 기준일 + 지금 시각 (실제 날짜로 남기면 다른 화면의 기간 계산과 어긋난다)
        LocalDateTime now = today.atTime(LocalTime.now().withNano(0));

        AllowanceChange change = allowanceChangeRepository.save(
                AllowanceChange.of(familyId, prevAmount, amount, effectiveFrom, reason, source, now));

        memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD)
                .ifPresent(child -> notificationRepository.save(Notification.of(
                        child, Notification.Type.ALLOWANCE_CHANGED, "용돈이 바뀌어요",
                        message(effectiveFrom, amount, reason), change.getChangeId(), now)));
        return change;
    }

    private static String message(LocalDate effectiveFrom, int amount, AllowanceChange.Reason reason) {
        String text = String.format(Locale.KOREA, "%d월부터 용돈이 %,d원이 돼요.", effectiveFrom.getMonthValue(), amount);
        return reason == AllowanceChange.Reason.INFLATION ? text + " 물가가 올라서 부모님이 올렸어요." : text;
    }
}
