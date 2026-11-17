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
}
