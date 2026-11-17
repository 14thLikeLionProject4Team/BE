package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.MyDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** 마이 화면의 프로필 영역 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final MemberRepository memberRepository;
    private final FamilySettingRepository familySettingRepository;
    private final SeedRepository seedRepository;

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
}
