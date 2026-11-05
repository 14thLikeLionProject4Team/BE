package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.PersonaResponse;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonaService {

    private final MemberRepository memberRepository;
    private final SeedRepository seedRepository;

    // 미션·정산 기능이 생기면 데모 기준일로 계산하도록 바꾸기
    private static final Map<Long, String> DESCRIPTIONS = Map.of(
            1L, "18세 → 19세로 넘어가는 중",
            2L, "막 만 19세가 됨");
    private static final Map<Long, String> STATUS_CHIPS = Map.of(
            1L, "확인 기다리는 미션 1",
            2L, "결제일 D-6 · 확인서 발급까지 D-14",
            3L, "기록이 본인에게 넘어감",
            4L, "확인서 발급 가능");

    public PersonaResponse getPersonas() {
        List<Member> members = memberRepository.findAll();

        // LinkedHashMap: 가족 순서(id 순) 유지
        Map<Long, List<Member>> byFamily = members.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getFamily().getFamilyId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<PersonaResponse.FamilyDto> families = byFamily.values().stream()
                .map(list -> new PersonaResponse.FamilyDto(
                        list.get(0).getFamily().getFamilyId(),
                        list.get(0).getFamily().getName(),
                        DESCRIPTIONS.get(list.get(0).getFamily().getFamilyId()),
                        list.stream().map(this::toMemberDto).toList()))
                .toList();

        return new PersonaResponse(seedRepository.today(), families);
    }

    private PersonaResponse.MemberDto toMemberDto(Member m) {
        return new PersonaResponse.MemberDto(
                m.getMemberId(),
                m.getName(),
                m.getRole(),
                STATUS_CHIPS.get(m.getMemberId()));
    }
}