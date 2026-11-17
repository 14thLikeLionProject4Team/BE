package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.BriefingDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.BriefingRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부모 홈 월간 브리핑. 브리핑은 매달 1일에 만들어지는 것으로 보고 시드에 넣어 둔다 (생성 스케줄러는 없음).
 * 부모만 보고, 아이가 만 19세가 되면 기록이 본인에게 넘어가서 403.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BriefingService {

    private static final int ADULT_AGE = 19;

    private final MemberRepository memberRepository;
    private final BriefingRepository briefingRepository;
    private final SeedRepository seedRepository;

    /** 가장 최근 브리핑. 아직 없으면 null */
    public BriefingDtos.Detail getLatest(Long personaId) {
        Member parent = parentBeforeAdult(personaId);
        return briefingRepository.findFirstByFamilyIdOrderByTargetMonthDesc(parent.getFamily().getFamilyId())
                .map(BriefingDtos.Detail::of)
                .orElse(null);
    }

    private Member parentBeforeAdult(Long personaId) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        boolean adult = memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD)
                .map(child -> !child.getBirthDate().plusYears(ADULT_AGE).isAfter(seedRepository.today()))
                .orElse(false);
        if (adult) throw new GeneralException(ErrorStatus.ADULT_TRANSITIONED);
        return parent;
    }
}
