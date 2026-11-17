package com.cheotdanchu.dto;

import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 가족 약속 설정 변경의 요청과 응답 */
public final class FamilySettingDtos {

    private FamilySettingDtos() {}

    /** capRate: 한 달 미션 보상 상한을 용돈의 몇 %로 둘지 (1~100). 빠지면 0 으로 읽혀 400 */
    public record MissionCapRequest(@Min(1) @Max(100) int capRate) {}

    /** capAmount 는 이번 정산 기간 용돈 기준 상한 금액이고, 용돈이 정해지지 않았으면 null */
    public record MissionCapResponse(int capRate, Long capAmount) {}

    /** 열람 범위 조회. 바꾸는 건 부모만이라 changeableBy 는 언제나 PARENT */
    public record VisibilityResponse(FamilySetting.Visibility visibility, String label, Member.Role changeableBy) {}

    /** visibility 는 문자열로 받아서 지원하지 않는 값에 "현재는 TOTAL_ONLY만 선택할 수 있어요" 를 알려준다 */
    public record VisibilityRequest(@NotNull String visibility) {}

    public record VisibilityChangeResponse(FamilySetting.Visibility visibility, String label) {}
}
