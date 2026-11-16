package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.FamilySettingDtos;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilySettingService {

    private final MemberRepository memberRepository;
    private final FamilySettingRepository familySettingRepository;
    private final MissionService missionService;

    /**
     * 한 달 미션 보상 상한 비율을 바꾼다. 이미 만든 미션은 그대로이고, 이후 미션 생성·열기·승인의 남은 한도 계산부터 새 비율을 쓴다.
     * 상한 금액은 월 보상 합계와 같은 계산(이번 정산 기간 용돈 × 비율)이라, 두 화면의 숫자가 같다.
     */
    @Transactional
    public FamilySettingDtos.MissionCapResponse updateMissionCap(Long personaId, int capRate) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);

        FamilySetting setting = familySettingRepository.findByIdForUpdate(parent.getFamily().getFamilyId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_SETTING_NOT_FOUND));
        setting.updateMissionCapRate(capRate);

        return new FamilySettingDtos.MissionCapResponse(capRate, missionService.getRewardSummary(personaId).capAmount());
    }
}
