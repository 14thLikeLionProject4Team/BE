package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Member;

import java.time.LocalDate;
import java.util.List;

public record PersonaResponse(
        LocalDate demoDate,
        List<FamilyDto> families
) {
    public record FamilyDto(
            Long familyId,
            String name,
            String description,
            List<MemberDto> members) {}

    public record MemberDto(
            Long memberId,
            String name,
            Member.Role role,
            String statusChip
    ) {}
}