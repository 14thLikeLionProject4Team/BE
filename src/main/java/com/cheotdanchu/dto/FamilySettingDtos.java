package com.cheotdanchu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 가족 약속 설정 변경의 요청과 응답 */
public final class FamilySettingDtos {

    private FamilySettingDtos() {}

    /** capRate: 한 달 미션 보상 상한을 용돈의 몇 %로 둘지 (1~100). 빠지면 0 으로 읽혀 400 */
    public record MissionCapRequest(@Min(1) @Max(100) int capRate) {}

    /** capAmount 는 이번 정산 기간 용돈 기준 상한 금액이고, 용돈이 정해지지 않았으면 null */
    public record MissionCapResponse(int capRate, Long capAmount) {}
}
