package com.cheotdanchu.service;

import com.cheotdanchu.ai.AiDtos;
import com.cheotdanchu.ai.AnswerGuard;
import com.cheotdanchu.ai.ChatFlowService;
import com.cheotdanchu.ai.ChatSessionStore;
import com.cheotdanchu.ai.RagCard;
import com.cheotdanchu.ai.RagRepository;
import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.ConversationDtos;
import com.cheotdanchu.entity.ChatMessage;
import com.cheotdanchu.entity.ChildMessage;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.ChatMessageRepository;
import com.cheotdanchu.repository.ChildMessageRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private static final int MAX_CONTENT = 500;

    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChildMessageRepository childMessageRepository;
    private final ChatFlowService chatFlowService;
    private final ChatSessionStore chatSessionStore;
    private final RagRepository ragRepository;
    private final SeedRepository seedRepository;
    private final SettlementRepository settlementRepository;

    private static final int PAGE_SIZE = 20;
    private static final List<String> RETROSPECT_QUESTIONS = List.of(
            "이번 달 가장 잘한 소비는 뭐였어?", "다음 달에 줄이고 싶은 게 있어?");
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

    /** 내 대화 기록 최신순, 20개씩. cursor는 이전 페이지 마지막 messageId */
    public ConversationDtos.MessagesResponse getMessages(Long personaId, Long cursor) {
        Member child = child(personaId);
        List<ChatMessage> page = chatMessageRepository.findByMemberIdAndMessageIdLessThanOrderByMessageIdDesc(
                child.getMemberId(), cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasNext = page.size() > PAGE_SIZE;
        List<ConversationDtos.Message> messages = page.stream().limit(PAGE_SIZE).map(ConversationDtos.Message::of).toList();
        return new ConversationDtos.MessagesResponse(messages, hasNext ? messages.get(messages.size() - 1).messageId() : null);
    }

    /**
     * 내 대화 기록을 모두 지운다. 이미 만든 전할 말은 남기고 출처 메시지 연결만 끊긴다(message_id = NULL).
     * 고민 대화 기억(이전 대화 맥락)도 함께 비워서, 지운 대화가 다음 답에 이어지지 않게 한다.
     */
    @Transactional
    public ConversationDtos.DeleteResponse deleteAll(Long personaId) {
        Member child = child(personaId);
        int deleted = chatMessageRepository.deleteAllByMemberId(child.getMemberId());
        String sessionKey = String.valueOf(child.getMemberId());
        chatSessionStore.clearWorry(sessionKey);
        chatSessionStore.setMemos(sessionKey, List.of());
        return new ConversationDtos.DeleteResponse(deleted);
    }

    /**
     * 전할 말 상태를 바꾼다. 자녀와 부모가 같은 API를 쓰고 역할마다 고를 수 있는 값이 다르다.
     * 자녀(후보일 때): MONTHLY·SENT(지금 전하기)·DISCARDED, 부모(전해진 말): DISCUSS·ACKNOWLEDGED.
     * 다른 가족의 말은 있는지도 알려주지 않으려고 404, 고를 수 없는 값이면 409.
     */
    @Transactional
    public ConversationDtos.StatusResponse changeStatus(Long personaId, Long childMessageId, ChildMessage.Status to) {
        Member member = memberRepository.getPersona(personaId);
        ChildMessage message = childMessageRepository.findByIdForUpdate(childMessageId)
                .filter(m -> m.getFamilyId().equals(member.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHILD_MESSAGE_NOT_FOUND));

        boolean byChild = member.getRole() == Member.Role.CHILD;
        if (to == null || !(byChild ? message.canChildChangeTo(to) : message.canParentChangeTo(to))) {
            throw new GeneralException(ErrorStatus.INVALID_STATUS_TRANSITION);
        }
        if (byChild) message.changeByChild(to, demoNow());
        else message.decideByParent(to, demoNow());
        return ConversationDtos.StatusResponse.of(message);
    }

    /** 상태를 바꾼 뒤 화면에 보여줄 문구 */
    public static String statusMessage(ChildMessage.Status status) {
        return switch (status) {
            case SENT -> "부모님께 전했어요.";
            case MONTHLY -> "월말 회고 때 전할지 다시 물어볼게요.";
            case DISCARDED -> "저장하지 않았어요.";
            case DISCUSS -> "같이 이야기하기로 했어요.";
            case ACKNOWLEDGED -> "확인했어요.";
            case CANDIDATE -> "전할 말 상태를 바꿨어요.";
        };
    }

    /**
     * 월말 회고: 가장 최근에 끝난 정산을 돌아보는 질문과, 월말에 전하기로 한(MONTHLY) 말.
     * 끝난 정산이 하나도 없으면 회고할 기간이 없어서 available=false.
     */
    public ConversationDtos.RetrospectResponse getRetrospect(Long personaId) {
        Member child = child(personaId);
        Long familyId = child.getFamily().getFamilyId();
        return latestSettlement(familyId)
                .map(s -> new ConversationDtos.RetrospectResponse(true, s.getSettlementId(), RETROSPECT_QUESTIONS,
                        childMessageRepository.findByFamilyIdAndStatusOrderByChildMessageIdAsc(familyId, ChildMessage.Status.MONTHLY)
                                .stream().map(ConversationDtos.PendingMessage::of).toList()))
                .orElse(new ConversationDtos.RetrospectResponse(false, null, List.of(), List.of()));
    }

    /**
     * 월말 회고에서 고른 전할 말을 부모님께 보낸다(SENT, delivery=MONTHLY). 고르지 않은 MONTHLY 말은 버린다(DISCARDED).
     * 끝난 정산이 없으면 409, 월말에 전하기로 한 말이 아닌 ID가 섞여 있으면 400.
     */
    @Transactional
    public ConversationDtos.ApproveResponse approveRetrospect(Long personaId, List<Long> approvedIds) {
        Member child = child(personaId);
        Long familyId = child.getFamily().getFamilyId();
        if (latestSettlement(familyId).isEmpty()) throw new GeneralException(ErrorStatus.RETROSPECT_NOT_AVAILABLE);

        Set<Long> approved = approvedIds == null ? Set.of() : new HashSet<>(approvedIds);
        List<ChildMessage> pending = childMessageRepository.findByFamilyIdAndStatusForUpdate(familyId, ChildMessage.Status.MONTHLY);
        Set<Long> pendingIds = pending.stream().map(ChildMessage::getChildMessageId).collect(Collectors.toSet());
        if (!pendingIds.containsAll(approved)) throw new GeneralException(ErrorStatus.INVALID_INPUT, "월말에 전하기로 한 말만 고를 수 있어요.");

        LocalDateTime now = demoNow();
        pending.forEach(m -> m.closeMonthly(approved.contains(m.getChildMessageId()), now));
        return new ConversationDtos.ApproveResponse(approved.size(), pending.size() - approved.size());
    }

    /** 가장 최근에 끝난 정산 (제외된 정산은 빼고) */
    private Optional<Settlement> latestSettlement(Long familyId) {
        List<Settlement> completed = settlementRepository.findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(
                familyId, Settlement.Status.COMPLETED);
        return completed.isEmpty() ? Optional.empty() : Optional.of(completed.get(completed.size() - 1));
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
