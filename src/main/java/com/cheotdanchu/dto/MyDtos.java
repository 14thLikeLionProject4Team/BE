package com.cheotdanchu.dto;

import com.cheotdanchu.entity.FamilySetting;

import java.time.LocalDate;
import java.util.List;

/** 마이 화면(내 정보·보호자 정보) */
public final class MyDtos {

    private MyDtos() {}

    public record ChildResponse(
            Long memberId,
            String name,
            LocalDate birthDate,
            int age,                          // 만 나이 (시연 기준일)
            boolean familyCardLinked,
            FamilySetting.Visibility visibility,
            AdultTransition adultTransition) {}

    /** 만 19세가 되면 transitioned=true 이고 at 은 만 19세가 된 날. 아직이면 at 은 null */
    public record AdultTransition(boolean transitioned, LocalDate at) {}

    public record ParentResponse(
            Long memberId,
            String name,
            String relation,                  // "{아이 이름} 보호자"
            boolean cardHolder,
            List<ChildSummary> children) {}

    public record ChildSummary(Long memberId, String name) {}

    /** "{아이}와의 약속" 카드. 값은 가족 설정과 용돈 이력에서 읽는다 */
    public record PromiseResponse(
            int streakMonths,
            Allowance allowance,
            int allowanceDay,
            boolean autoTransfer,
            int settlementDay,
            FamilySetting.Visibility visibility,
            int missionCapRate,
            Long missionCapAmount,            // 용돈이 정해지지 않았으면 null
            Integer cardLimit) {}             // 카드사가 정하므로 보기만 하고, 정해지지 않았으면 null

    /** 현재 용돈과 예정 용돈. 바뀔 예정이 없으면 next·nextEffectiveFrom 은 null */
    public record Allowance(Integer current, Integer next, LocalDate nextEffectiveFrom) {}

    /** 성년 전환 카드. 전환 전이면 transitioned=false 이고 나머지는 null/false */
    public record AdultTransitionCard(
            boolean transitioned,
            LocalDate at,
            Long togetherCount,               // 함께한 결제일 횟수 (전환일까지 끝난 정산 수)
            boolean recordsHidden) {}
}
