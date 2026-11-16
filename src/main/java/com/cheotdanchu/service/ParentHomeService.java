package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.ParentHomeDtos;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    public ParentHomeDtos.HomeResponse getHome(Long personaId) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        Long familyId = parent.getFamily().getFamilyId();
        Member child = memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));

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
