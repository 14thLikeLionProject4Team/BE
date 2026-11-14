package com.cheotdanchu.service;

import com.cheotdanchu.ai.JsonUtil;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.AllowanceSuggestionDtos;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.AllowanceSuggestion;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.AllowanceSuggestionRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllowanceSuggestionService {

    private final MemberRepository memberRepository;
    private final AllowanceSuggestionRepository suggestionRepository;
    private final AllowanceChangeService allowanceChangeService;

    /** 대기 중인 제안이 없으면 null (에러가 아니라 "제안 없음"). 제안을 만든 뒤 용돈이 바뀌어 오래된 제안도 보여주지 않는다 */
    public AllowanceSuggestionDtos.SuggestionResponse getPending(Long personaId) {
        Member parent = parent(personaId);
        Long familyId = parent.getFamily().getFamilyId();
        return suggestionRepository
                .findFirstByFamilyIdAndStatusOrderByCreatedAtDesc(familyId, AllowanceSuggestion.Status.PENDING)
                .filter(s -> !isOutdated(s, familyId))
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * 제안이 만들어질 때의 현재 용돈과, 지금 적용 후보 날(다음 달 1일)에 적용되는 용돈이 다르면 오래된 제안이다.
     * 그 사이 부모가 용돈을 직접 정했다면, 옛 제안을 적용해 그 결정을 덮어쓰지 않게 한다.
     */
    private boolean isOutdated(AllowanceSuggestion suggestion, Long familyId) {
        return allowanceChangeService.amountOn(familyId, allowanceChangeService.nextEffectiveDate())
                .map(amount -> amount != suggestion.getCurrentAmount())
                .orElse(false);
    }

    /**
     * 제안을 처리한다. 적용(APPLIED)은 다음 달 1일부터 바뀌는 용돈 변경 이력(AI 제안·물가)을 남기고 아이에게 알리며,
     * 유지(KEPT)는 용돈을 그대로 두고 제안만 닫는다. 제안 행에 잠금을 걸어서 같은 제안이 두 번 처리되지 않게 한다.
     */
    @Transactional
    public AllowanceSuggestionDtos.DecisionResponse decide(Long personaId, Long suggestionId, AllowanceSuggestion.Status status) {
        Member parent = parent(personaId);
        if (status == AllowanceSuggestion.Status.PENDING) throw new GeneralException(ErrorStatus.INVALID_INPUT);

        AllowanceSuggestion suggestion = suggestionRepository.findByIdForUpdate(suggestionId)
                .filter(s -> s.getFamilyId().equals(parent.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.SUGGESTION_NOT_FOUND));
        if (!suggestion.isPending()) throw new GeneralException(ErrorStatus.ALREADY_DECIDED);
        if (isOutdated(suggestion, parent.getFamily().getFamilyId())) throw new GeneralException(ErrorStatus.SUGGESTION_OUTDATED);

        if (status == AllowanceSuggestion.Status.KEPT) {
            suggestion.keep();
            return new AllowanceSuggestionDtos.DecisionResponse(suggestionId, suggestion.getStatus(), suggestion.getCurrentAmount(), null);
        }

        AllowanceChange change = allowanceChangeService.record(
                parent, suggestion.getSuggestedAmount(), AllowanceChange.Reason.INFLATION, AllowanceChange.Source.AI_SUGGESTION);
        suggestion.apply();
        return new AllowanceSuggestionDtos.DecisionResponse(
                suggestionId, suggestion.getStatus(), suggestion.getSuggestedAmount(), change.getEffectiveFrom());
    }

    private Member parent(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        return member;
    }

    private AllowanceSuggestionDtos.SuggestionResponse toResponse(AllowanceSuggestion s) {
        return new AllowanceSuggestionDtos.SuggestionResponse(
                s.getSuggestionId(), s.getStatus(), s.getCurrentAmount(), s.getSuggestedAmount(),
                s.getRangeMin(), s.getRangeMax(), s.getInflationRate(), basisOf(s.getBasis()),
                s.getExplanation(), s.getCreatedAt());
    }

    /**
     * 근거(카테고리·월 사용액·변동률)를 읽는다. 근거가 깨져 있어도 제안 카드 자체는 보여야 해서,
     * 읽지 못하면 빈 목록으로, 항목에 빠진 값이 있으면 그 항목만 건너뛴다.
     */
    private static List<AllowanceSuggestionDtos.Basis> basisOf(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<Map<String, Object>> items;
        try {
            items = JsonUtil.parseArray(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("AI 제안 근거를 읽지 못했어요: {}", json, e);
            return List.of();
        }
        return items.stream()
                .filter(m -> m.get("category") != null && m.get("monthlySpent") instanceof Number && m.get("changeRate") instanceof Number)
                .map(m -> new AllowanceSuggestionDtos.Basis(
                        String.valueOf(m.get("category")),
                        ((Number) m.get("monthlySpent")).intValue(),
                        ((Number) m.get("changeRate")).doubleValue()))
                .toList();
    }
}
