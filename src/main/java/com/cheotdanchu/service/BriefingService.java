package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.BriefingDtos;
import com.cheotdanchu.entity.Briefing;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.BriefingRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * 부모 홈 월간 브리핑. 브리핑은 매달 1일에 만들어지는 것으로 보고 시드에 넣어 둔다 (생성 스케줄러는 없음).
 * 부모만 보고, 아이가 만 19세가 되면 기록이 본인에게 넘어가서 403.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BriefingService {

    private static final int ADULT_AGE = 19;
    private static final int PAGE_SIZE = 12;

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

    /** 지난 브리핑 최신순, 12개씩 (부모 마이의 지난 브리핑에서도 쓴다) */
    public BriefingDtos.ListResponse getBriefings(Long personaId, Long cursor) {
        Long familyId = parentBeforeAdult(personaId).getFamily().getFamilyId();
        List<Briefing> page = briefingRepository.findByFamilyIdAndBriefingIdLessThanOrderByBriefingIdDesc(
                familyId, cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasNext = page.size() > PAGE_SIZE;
        List<BriefingDtos.Item> items = page.stream().limit(PAGE_SIZE).map(BriefingDtos.Item::of).toList();
        return new BriefingDtos.ListResponse(briefingRepository.countByFamilyId(familyId), items,
                hasNext ? items.get(items.size() - 1).briefingId() : null);
    }

    /** 내 가족 브리핑만 읽음 처리한다. 다른 가족 것은 404, 이미 읽었어도 성공 */
    @Transactional
    public void markRead(Long personaId, Long briefingId) {
        Long familyId = parentBeforeAdult(personaId).getFamily().getFamilyId();
        Briefing briefing = briefingRepository.findById(briefingId)
                .filter(b -> b.getFamilyId().equals(familyId))
                .orElseThrow(() -> new GeneralException(ErrorStatus.BRIEFING_NOT_FOUND));
        // 다른 기록 시각과 맞춰 데모 기준일 + 지금 시각
        briefing.markRead(seedRepository.today().atTime(LocalTime.now().withNano(0)));
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
