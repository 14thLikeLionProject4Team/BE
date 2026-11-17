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

import java.util.Arrays;
import java.util.List;

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

    /** 열람 범위 조회: 부모·자녀 모두 볼 수 있다. 가족 설정이 없으면 기본값(총액만, 부모 홈·자녀 홈과 같다) */
    @Transactional(readOnly = true)
    public FamilySettingDtos.VisibilityResponse getVisibility(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        FamilySetting.Visibility visibility = familySettingRepository.visibilityOf(member.getFamily().getFamilyId());
        return new FamilySettingDtos.VisibilityResponse(visibility, visibility.getLabel(), Member.Role.PARENT);
    }

    /** 열람 범위 변경: 부모만. 지금은 TOTAL_ONLY 만 고를 수 있어서 다른 값은 400 이다 */
    @Transactional
    public FamilySettingDtos.VisibilityChangeResponse updateVisibility(Long personaId, String value) {
        Member parent = memberRepository.getParent(personaId);
        FamilySetting.Visibility visibility = parseVisibility(value);

        FamilySetting setting = familySettingRepository.findByIdForUpdate(parent.getFamily().getFamilyId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_SETTING_NOT_FOUND));
        setting.updateVisibility(visibility);
        return new FamilySettingDtos.VisibilityChangeResponse(visibility, visibility.getLabel());
    }

    private static FamilySetting.Visibility parseVisibility(String value) {
        try {
            return FamilySetting.Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, notSelectableMessage());
        }
    }

    /** 고를 수 있는 값을 enum 에서 읽어 안내한다(값이 늘어도 문구가 맞는다). 지금은 TOTAL_ONLY 하나뿐이다 */
    private static String notSelectableMessage() {
        List<String> names = Arrays.stream(FamilySetting.Visibility.values()).map(Enum::name).toList();
        return names.size() == 1
                ? "visibility: 현재는 " + names.get(0) + "만 선택할 수 있어요"
                : "visibility: " + String.join(", ", names) + " 중에서 선택해 주세요";
    }
}
