package com.cheotdanchu.dto;

import com.cheotdanchu.entity.ChatMessage;
import com.cheotdanchu.entity.ChildMessage;

import java.time.LocalDateTime;
import java.util.Map;

/** 자녀 대화창 요청·응답 */
public final class ConversationDtos {

    private ConversationDtos() {}

    public record SendRequest(String content) {}

    /** childMessage: 이번 답에서 전할 말 후보가 생겼을 때만 (없으면 null) */
    public record SendResponse(Message reply, ChildMessageItem childMessage) {}

    public record Message(
            Long messageId,
            ChatMessage.Sender sender,
            ChatMessage.CardType cardType,
            String content,
            Map<String, Object> payload,
            LocalDateTime createdAt
    ) {
        public static Message of(ChatMessage m) {
            return new Message(m.getMessageId(), m.getSender(), m.getCardType(), m.getContent(), m.getPayload(), m.getCreatedAt());
        }
    }

    public record ChildMessageItem(Long childMessageId, String request, String reason, ChildMessage.Status status) {
        public static ChildMessageItem of(ChildMessage m) {
            return new ChildMessageItem(m.getChildMessageId(), m.getRequest(), m.getReason(), m.getStatus());
        }
    }
}
