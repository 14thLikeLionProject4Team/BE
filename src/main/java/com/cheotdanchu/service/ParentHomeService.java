package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.AllowanceSuggestionDtos;
import com.cheotdanchu.dto.MissionQueryDtos;
import com.cheotdanchu.dto.ParentHomeDtos;
import com.cheotdanchu.entity.ChildMessage;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.ChildMessageRepository;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
public class ParentHomeService {

    /** 만 19세가 되면 기록이 본인에게 넘어간다 (부모에게는 성년 전환 표시만) */
    private static final int ADULT_AGE = 19;

    private final MemberRepository memberRepository;
    private final FamilySettingRepository familySettingRepository;
    private final NotificationRepository notificationRepository;
    private final SettlementRepository settlementRepository;
    private final SeedRepository seedRepository;
    private final AllowanceSuggestionService allowanceSuggestionService;
    private final MissionService missionService;
    private final ChildMessageRepository childMessageRepository;

    public ParentHomeDtos.HomeResponse getHome(Long personaId) {
        Member parent = parent(personaId);
        Long familyId = parent.getFamily().getFamilyId();
        Member child = childOf(parent);

        LocalDate today = seedRepository.today();
        LocalDate adultDay = child.getBirthDate().plusYears(ADULT_AGE);
        boolean adult = !adultDay.isAfter(today);
        FamilySetting.Visibility visibility = familySettingRepository.findById(familyId)
                .map(FamilySetting::getVisibility)
                .orElse(FamilySetting.Visibility.TOTAL_ONLY);

        return new ParentHomeDtos.HomeResponse(
                today,
                child.getName(),
                visibility,
                "총액만 보는 중 · " + child.getName() + "에게도 이렇게 보여요",
                notificationRepository.countByMember_MemberIdAndReadAtIsNull(parent.getMemberId()),
                adult ? List.of() : praiseChips(familyId, today, adultDay),
                adult);
    }

    /**
     * 부모가 지금 처리할 카드만 모은다 (별도 테이블 없음): AI 용돈 제안 → 아이가 전한 말 → 미션 확인 순.
     * 전할 말은 아이가 전했고(SENT) 부모가 아직 답하지 않은 것만. 카드가 없으면 빈 배열 (화면: "지금 정할 건 없어요")
     */
    public ParentHomeDtos.DecisionsResponse getDecisions(Long personaId) {
        Member parent = parent(personaId);
        LocalDate today = seedRepository.today();
        List<ParentHomeDtos.DecisionCard> cards = new ArrayList<>();

        AllowanceSuggestionDtos.SuggestionResponse suggestion = allowanceSuggestionService.getPending(parent);
        if (suggestion != null) {
            String childName = childOf(parent).getName();
            int diff = suggestion.suggestedAmount() - suggestion.currentAmount();
            cards.add(new ParentHomeDtos.DecisionCard(ParentHomeDtos.CardType.ALLOWANCE_SUGGESTION, suggestion.suggestionId(),
                    String.format(Locale.KOREA, "%s 결제 품목 물가가 %s%% 올랐어요", childName, suggestion.inflationRate().stripTrailingZeros().toPlainString()),
                    String.format(Locale.KOREA, "월 %,d원 → %,d원 (%+,d원)을 제안해요.", suggestion.currentAmount(), suggestion.suggestedAmount(), diff),
                    "AI 용돈 제안"));
        }

        for (ChildMessage m : childMessageRepository.findByFamilyIdAndStatus(
                parent.getFamily().getFamilyId(), ChildMessage.Status.SENT, Sort.by("sentAt", "childMessageId"))) {
            cards.add(new ParentHomeDtos.DecisionCard(ParentHomeDtos.CardType.CHILD_MESSAGE, m.getChildMessageId(),
                    m.getRequest(), m.getReason(),
                    m.getDelivery() == ChildMessage.Delivery.MONTHLY ? "월말 회고에서 전한 말" : "아이가 전한 말"));
        }

        for (MissionQueryDtos.PendingSubmission s : missionService.getPendingSubmissions(personaId).submissions()) {
            cards.add(new ParentHomeDtos.DecisionCard(ParentHomeDtos.CardType.MISSION_REVIEW, s.submissionId(), s.missionTitle(),
                    s.rewardAmount() == null ? null : String.format(Locale.KOREA, "+%,d원", s.rewardAmount()),
                    submittedLabel(s.submittedAt().toLocalDate(), today)));
        }
        return new ParentHomeDtos.DecisionsResponse(cards.size(), cards);
    }

    private static String submittedLabel(LocalDate submitted, LocalDate today) {
        long days = ChronoUnit.DAYS.between(submitted, today);
        if (days <= 0) return "오늘 제출";
        if (days == 1) return "어제 제출";
        return submitted.getMonthValue() + "월 " + submitted.getDayOfMonth() + "일 제출";
    }

    private Member parent(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        return member;
    }

    private Member childOf(Member parent) {
        return memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));
    }

    /** 진행 상황 칭찬 칩: 최근 연속 완납 개월, 확인서 발급(만 19세)까지 남은 날 */
    private List<String> praiseChips(Long familyId, LocalDate today, LocalDate adultDay) {
        List<String> chips = new ArrayList<>();
        int streak = currentStreak(familyId);
        if (streak > 0) chips.add(streak + "개월 연속 결제일 완납");
        chips.add("확인서 발급까지 D-" + ChronoUnit.DAYS.between(today, adultDay));
        return chips;
    }

    /** 가장 최근 끝난 정산부터 거슬러 올라가며 연속으로 완납한 개월 수 (제외된 정산은 건너뛴다) */
    private int currentStreak(Long familyId) {
        List<Settlement> settled = settlementRepository.findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(
                familyId, Settlement.Status.COMPLETED);
        int streak = 0;
        for (int i = settled.size() - 1; i >= 0 && settled.get(i).getResult() == Settlement.Result.FULL_PAID; i--) streak++;
        return streak;
    }
}
