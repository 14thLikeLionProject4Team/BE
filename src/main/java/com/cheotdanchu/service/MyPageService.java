package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.MyDtos;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** 마이 화면: 프로필(내 정보·보호자 정보)과 부모의 약속 요약·성년 전환 카드 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final MemberRepository memberRepository;
    private final FamilySettingRepository familySettingRepository;
    private final SeedRepository seedRepository;
    private final SettlementRepository settlementRepository;
    private final AllowanceChangeService allowanceChangeService;
    private final RecordsService recordsService;
    private final MissionService missionService;

    /** 자녀의 내 정보: 프로필, 가족카드 연결 여부, 열람 범위, 성년 전환 여부 */
    public MyDtos.ChildResponse getChildMy(Long personaId) {
        Member child = memberRepository.getChild(personaId);
        LocalDate today = seedRepository.today();
        boolean transitioned = child.isAdultOn(today);

        return new MyDtos.ChildResponse(
                child.getMemberId(),
                child.getName(),
                child.getBirthDate(),
                child.ageOn(today),
                true, // 시연: 가족카드 연결은 항상 켜짐 (현재 정산 조회의 cardLinked 와 같은 값. 카드사 연동 방식은 명세상 미정)
                familySettingRepository.visibilityOf(child.getFamily().getFamilyId()),
                new MyDtos.AdultTransition(transitioned, transitioned ? child.adultDay() : null));
    }

    /** 부모의 정보: 보호자 이름·관계, 가족카드 본인회원 여부, 아이 선택 목록(지금은 아이 1명) */
    public MyDtos.ParentResponse getParentMy(Long personaId) {
        Member parent = memberRepository.getParent(personaId);
        Optional<Member> child = memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD);

        return new MyDtos.ParentResponse(
                parent.getMemberId(),
                parent.getName(),
                child.map(c -> c.getName() + " 보호자").orElse(null),
                true, // 시연: 부모는 가족카드 본인회원 (회원 구분 데이터는 없음)
                child.map(c -> new MyDtos.ChildSummary(c.getMemberId(), c.getName())).stream().toList());
    }

    /**
     * "{아이}와의 약속" 카드: 연속 완납 배지, 월 용돈(현재·예정), 보내는 날, 자동이체, 결제일, 열람 범위, 미션 상한, 카드 한도.
     * 예정 용돈은 다음 달 1일부터 적용될 금액이 지금과 다를 때만 준다. 미션 상한 금액은 월 보상 합계·상한 변경과 같은 계산이다.
     * 아이가 만 19세가 되면 403(기록이 본인에게 넘어갔어요). 성년 전환 카드가 이 카드를 대신한다.
     * childId 는 선택이고(약속은 가족 단위) 안 주면 첫째 아이, 우리 가족의 아이가 아니면 404 다.
     */
    public MyDtos.PromiseResponse getPromise(Long personaId, Long childId) {
        Member parent = memberRepository.getParent(personaId);
        Member child = memberRepository.getChildOfFamily(parent, childId);
        // 아이가 만 19세가 되면 기록(연속 완납 등)은 본인만 보고 부모에게는 성년 전환 카드만 보인다 (정산 요약·브리핑과 같은 규칙)
        LocalDate today = seedRepository.today();
        if (child.isAdultOn(today)) throw new GeneralException(ErrorStatus.ADULT_TRANSITIONED);
        Long familyId = parent.getFamily().getFamilyId();

        FamilySetting setting = familySettingRepository.findById(familyId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_SETTING_NOT_FOUND));
        Integer current = allowanceChangeService.amountOn(familyId, today).orElse(null);
        Integer scheduled = allowanceChangeService.scheduledAmount(familyId).orElse(null);
        boolean changing = scheduled != null && !scheduled.equals(current);

        return new MyDtos.PromiseResponse(
                recordsService.currentStreak(familyId),
                new MyDtos.Allowance(current, changing ? scheduled : null, changing ? allowanceChangeService.nextEffectiveDate() : null),
                setting.getAllowanceDay(),
                setting.isAutoTransfer(),
                setting.getSettlementDay(),
                setting.getVisibility(),
                setting.getMissionCapRate(),
                missionService.getRewardSummary(personaId).capAmount(),
                setting.getCardLimit());
    }

    /**
     * 성년 전환 카드: 아이가 만 19세가 되면 약속·브리핑 자리를 대체하는 "기록이 본인에게 넘어갔어요" 카드.
     * 전환 뒤 기록은 본인만 보므로 함께한 결제일 횟수(전환일까지 끝난 정산 수)만 주고 숫자 기록은 주지 않는다. 전환 전이면 나머지는 null.
     */
    public MyDtos.AdultTransitionCard getAdultTransition(Long personaId, Long childId) {
        Member child = memberRepository.getChildOfFamily(memberRepository.getParent(personaId), childId);
        if (!child.isAdultOn(seedRepository.today())) return new MyDtos.AdultTransitionCard(false, null, null, false);

        long together = settlementRepository.countByFamilyIdAndStatusAndPeriodEndLessThanEqual(
                child.getFamily().getFamilyId(), Settlement.Status.COMPLETED, child.adultDay());
        return new MyDtos.AdultTransitionCard(true, child.adultDay(), together, true);
    }
}
