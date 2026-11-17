package com.cheotdanchu.dto;

import com.cheotdanchu.entity.FamilySetting;

import java.time.LocalDate;
import java.util.List;

public final class ChildHomeDtos {

    private ChildHomeDtos() {}

    public record HomeResponse(
            LocalDate today,
            Member member,
            Chips chips,
            CurrentSettlementResponse settlement, // 진행 중인 정산이 없으면 null
            boolean shortfallCheckShow) {}

    public record Member(Long memberId, String name) {}

    /** 상태 칩 3개의 값. 문구는 화면에서 만든다 */
    public record Chips(
            int streakMonths,        // 지금까지 이어진 연속 완납 개월 수 (0이면 칩을 숨긴다)
            Long certificateDDay,    // 확인서 발급 가능일까지 남은 날. 오늘이 그날이면 0, 이미 발급할 수 있으면 null
            FamilySetting.Visibility visibility) {}

    public record DailySpendingResponse(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate today,
            Long allowanceAmount,    // 이번 달 용돈(보내는 날에 적용 중인 금액, 부모 홈 카드와 같은 기준). 용돈 정보가 없으면 null
            List<DailyItem> items) {}

    public record DailyItem(LocalDate date, long amount) {} // 취소가 합산돼 마이너스일 수 있다 (그래프는 화면에서 0 이하를 0으로 그린다)
}
