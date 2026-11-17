package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.MemberRepository;
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
 * AI 제안 "다음 달부터 적용"과 용돈 변경 저장(AllowanceSettingService)이 같이 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AllowanceChangeService {

    private final AllowanceChangeRepository allowanceChangeRepository;
    private final MemberRepository memberRepository;
    private final NotificationSender notificationSender;
    private final SeedRepository seedRepository;

    /** 지금 정하면 적용되는 날. 정산 기간 중간에는 바꾸지 않으므로 다음 달 1일이다 */
    public LocalDate nextEffectiveDate() {
        return YearMonth.from(seedRepository.today()).plusMonths(1).atDay(1);
    }

    /** 지금 정하면 적용되는 날(다음 달 1일)에 적용될 월 용돈. 이미 정해 둔 용돈이 있으면 그 값 */
    public Optional<Integer> scheduledAmount(Long familyId) {
        return amountOn(familyId, nextEffectiveDate());
    }

    /** 그날 적용 중인 월 용돈 */
    public Optional<Integer> amountOn(Long familyId, LocalDate date) {
        return allowanceChangeRepository
                .findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescChangeIdDesc(familyId, date)
                .map(AllowanceChange::getAmount);
    }

    @Transactional
    public AllowanceChange record(Member parent, int amount, AllowanceChange.Reason reason, AllowanceChange.Source source) {
        LocalDate today = seedRepository.today();
        Long familyId = parent.getFamily().getFamilyId();
        LocalDate effectiveFrom = nextEffectiveDate();
        // 이미 같은 날부터 적용될 용돈이 정해져 있으면 그것을 이전 금액으로 본다 (이력이 72,000 → 74,000 처럼 이어진다)
        Integer prevAmount = scheduledAmount(familyId).orElse(null);
        // 이력·알림 시각은 데모 기준일 + 지금 시각 (실제 날짜로 남기면 다른 화면의 기간 계산과 어긋난다)
        LocalDateTime now = today.atTime(LocalTime.now().withNano(0));

        AllowanceChange change = allowanceChangeRepository.save(
                AllowanceChange.of(familyId, prevAmount, amount, effectiveFrom, reason, source, now));

        memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD)
                .ifPresent(child -> notificationSender.send(Notification.of(
                        child, Notification.Type.ALLOWANCE_CHANGED, "용돈이 바뀌어요",
                        message(effectiveFrom, amount, prevAmount, reason), change.getChangeId(), now)));
        return change;
    }

    /**
     * 저장하면 아이에게 가는 알림 문구. 저장 전 미리보기도 같은 문구를 쓴다.
     * 물가 때문이라는 설명은 금액이 실제로 오를 때만 붙인다 (물가를 고르고 내리거나 처음 정한 용돈이면 붙이지 않는다).
     */
    public static String message(LocalDate effectiveFrom, int amount, Integer prevAmount, AllowanceChange.Reason reason) {
        String text = String.format(Locale.KOREA, "%d월부터 용돈이 %,d원이 돼요.", effectiveFrom.getMonthValue(), amount);
        boolean raised = prevAmount != null && amount > prevAmount;
        return reason == AllowanceChange.Reason.INFLATION && raised ? text + " 물가가 올라서 부모님이 올렸어요." : text;
    }
}
