package com.cheotdanchu.service;

import com.cheotdanchu.ai.AiDtos;
import com.cheotdanchu.ai.AnswerGuard;
import com.cheotdanchu.ai.ChatFlowService;
import com.cheotdanchu.ai.RagCard;
import com.cheotdanchu.ai.RagRepository;
import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.ConversationDtos;
import com.cheotdanchu.entity.ChatMessage;
import com.cheotdanchu.entity.ChildMessage;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.ChatMessageRepository;
import com.cheotdanchu.repository.ChildMessageRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private static final int MAX_CONTENT = 500;

    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChildMessageRepository childMessageRepository;
    private final ChatFlowService chatFlowService;
    private final RagRepository ragRepository;
    private final SeedRepository seedRepository;
    private final SettlementRepository settlementRepository;

    private static final String PRIVACY_NOTICE = "부모님은 이 대화를 볼 수 없어요. 네가 허락한 말만 전해져.";
    private static final List<String> CHIPS = List.of("용돈이 부족해요", "돈 모으는 법", "부모님께 할 말 있어요");

    /** 대화 탭 첫 인사(이번 정산 결제일까지 남은 날), 부모 비공개 안내, 추천 칩 */
    public ConversationDtos.StarterResponse getStarter(Long personaId) {
        Member child = child(personaId);
        LocalDate today = seedRepository.today();
        String hello = child.getName() + vocative(child.getName()) + " 안녕! ";
        String greeting = settlementRepository.findCurrent(child.getFamily().getFamilyId(), today)
                .map(s -> ChronoUnit.DAYS.between(today, s.getDueAt().toLocalDate()))
                .map(days -> hello + (days <= 0 ? "오늘이 결제일이야." : "이번 달 결제일까지 " + days + "일 남았어."))
                .orElse(hello + "궁금한 걸 편하게 물어봐.");
        return new ConversationDtos.StarterResponse(greeting, PRIVACY_NOTICE, CHIPS);
    }

    /** 부르는 말: 받침이 있으면 "아"(지민아), 없으면 "야"(루피야) */
    static String vocative(String name) {
        if (name.isEmpty()) return "야";
        char last = name.charAt(name.length() - 1);
        boolean hangul = last >= 0xAC00 && last <= 0xD7A3;
        return hangul && (last - 0xAC00) % 28 != 0 ? "아" : "야";
    }

    /**
     * 자녀 메시지를 저장하고 첫단추 답을 받아 함께 저장한다. 고민 대화에서 부모님께 바라는 게 나오면 전할 말 후보도 만든다.
     * 대화창 흐름(분류 → 금융 설명·고민·내 숫자)은 ChatFlowService 그대로이고, 고민 대화 기억의 키는 memberId다.
     */
    @Transactional
    public ConversationDtos.SendResponse send(Long personaId, String content) {
        Member child = child(personaId);
        if (content == null || content.isBlank()) throw new GeneralException(ErrorStatus.INVALID_INPUT, "메시지를 입력해 주세요.");
        String text = content.strip();
        // 긴 글을 그대로 LLM에 넣지 않도록 길이를 막는다
        if (text.codePointCount(0, text.length()) > MAX_CONTENT) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, "메시지는 " + MAX_CONTENT + "자까지 보낼 수 있어요.");
        }

        LocalDateTime now = demoNow();
        ChatMessage asked = chatMessageRepository.save(ChatMessage.fromChild(child.getMemberId(), text, now));
        AiDtos.ChatResponse answer = chatFlowService.chat(String.valueOf(child.getMemberId()), child.getName(), text);

        ChatMessage reply = chatMessageRepository.save(
                ChatMessage.fromBot(child.getMemberId(), answer.answer(), cardType(answer), payload(answer), now));
        ChildMessage candidate = answer.toParent() == null ? null : childMessageRepository.save(ChildMessage.candidate(
                child.getFamily().getFamilyId(), asked.getMessageId(), answer.toParent().request(), answer.toParent().reason(), now));

        return new ConversationDtos.SendResponse(ConversationDtos.Message.of(reply),
                candidate == null ? null : ConversationDtos.ChildMessageItem.of(candidate));
    }

    private static ChatMessage.CardType cardType(AiDtos.ChatResponse answer) {
        return switch (answer.kind()) {
            case "금융지식" -> ChatMessage.CardType.RAG;
            case "내숫자" -> ChatMessage.CardType.NUMBER;
            default -> ChatMessage.CardType.TEXT;
        };
    }

    /** 금융 설명: 자료 범위 안인지와 출처(용어·출처 이름). 위기 안내면 crisis만. 그 밖에는 null */
    private Map<String, Object> payload(AiDtos.ChatResponse answer) {
        if (answer.crisis()) return Map.of("crisis", true);
        if (!"금융지식".equals(answer.kind())) return null;

        List<Map<String, String>> sources = new ArrayList<>();
        for (String id : answer.usedIds()) {
            RagCard card = ragRepository.byId(id);
            if (card == null) continue;
            for (RagCard.Source s : card.sources()) sources.add(Map.of("title", card.title(), "source", s.name()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inScope", !AnswerGuard.REFUSAL.equals(answer.answer()));
        payload.put("sources", sources);
        return payload;
    }

    private Member child(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.CHILD_ONLY);
        return member;
    }

    /** 다른 기록 시각과 맞춰 데모 기준일 + 지금 시각 */
    private LocalDateTime demoNow() {
        return seedRepository.today().atTime(LocalTime.now().withNano(0));
    }
}
