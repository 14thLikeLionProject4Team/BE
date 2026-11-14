package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.AllowanceChangeDtos;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.AllowanceSuggestion;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AllowanceSettingService {

    private final MemberRepository memberRepository;
    private final FamilySettingRepository familySettingRepository;
    private final AllowanceSuggestionService suggestionService;
    private final AllowanceChangeService allowanceChangeService;

    /**
     * 보내는 날·자동이체·결제일을 저장하고, 금액이 바뀌면 다음 달 1일부터 적용되는 이력을 남기고 아이에게 알린다.
     * 금액이 이미 정해진 값과 같으면(보내는 날만 바꾼 경우 등) 이력과 알림은 만들지 않는다.
     * AI 제안에서 온 저장(suggestionId)은 제안한 금액 그대로일 때만 AI 제안을 따른 것으로 기록하고,
     * 다른 금액이면 부모가 직접 정한 것으로 남기고 제안은 쓰지 않은 것으로 닫는다.
     */
    // 잠금을 기다리다 풀린 뒤에는 앞 요청이 저장한 이력이 보여야 해서 READ_COMMITTED 로 읽는다 (기본값은 트랜잭션 처음 읽은 시점의 모습만 본다)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AllowanceChangeDtos.SaveResponse save(Long personaId, AllowanceChangeDtos.SaveRequest request) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        if (request.reason() == AllowanceChange.Reason.INITIAL) throw new GeneralException(ErrorStatus.INVALID_INPUT);
        Long familyId = parent.getFamily().getFamilyId();

        // 같은 가족의 저장 요청(연타)이 한 줄로 처리되도록 가족 설정 행을 먼저 잠근다
        FamilySetting setting = familySettingRepository.findByIdForUpdate(familyId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_SETTING_NOT_FOUND));
        AllowanceSuggestion suggestion = request.suggestionId() == null
                ? null : suggestionService.lockPending(familyId, request.suggestionId());
        setting.updateAllowanceSettings(request.allowanceDay(), request.autoTransfer(), request.settlementDay());

        boolean followsSuggestion = suggestion != null && request.amount() == suggestion.getSuggestedAmount();
        AllowanceChange.Reason reason = request.reason() == null && followsSuggestion
                ? AllowanceChange.Reason.INFLATION : request.reason();

        LocalDate effectiveFrom = allowanceChangeService.nextEffectiveDate();
        Integer scheduled = allowanceChangeService.amountOn(familyId, effectiveFrom).orElse(null);
        AllowanceChange change = null;
        if (scheduled == null || scheduled != request.amount()) {
            change = allowanceChangeService.record(parent, request.amount(), reason,
                    followsSuggestion ? AllowanceChange.Source.AI_SUGGESTION : AllowanceChange.Source.PARENT);
        }
        if (suggestion != null) {
            if (followsSuggestion) suggestion.apply();
            else suggestion.keep();
        }

        if (change == null) {
            return new AllowanceChangeDtos.SaveResponse(null, scheduled, request.amount(), effectiveFrom, false);
        }
        return new AllowanceChangeDtos.SaveResponse(
                change.getChangeId(), change.getPrevAmount(), change.getAmount(), change.getEffectiveFrom(), true);
    }
}
