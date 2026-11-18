package com.cheotdanchu.dto;

import com.cheotdanchu.entity.ChatMessage;
import com.cheotdanchu.entity.ChildMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 자녀 대화창 요청·응답 */
public final class ConversationDtos {

    private ConversationDtos() {}

    public record StarterResponse(String greeting, String privacyNotice, List<String> chips) {}

    public record SendRequest(String content) {}

    public record MessagesResponse(List<Message> messages, Long nextCursor) {}

    public record DeleteResponse(int deletedCount) {}

    public record StatusRequest(ChildMessage.Status status) {}

    /** available=false(정산 전)면 settlementId null, questions·pendingMessages 빈 배열 */
    public record RetrospectResponse(boolean available, Long settlementId, List<String> questions, List<PendingMessage> pendingMessages) {}

    /** 월말 회고에서 부모님께 보낼 전할 말 ID. 비우면 모두 버린다 */
    public record ApproveRequest(List<Long> approvedIds) {}

    public record ApproveResponse(int sentCount, int discardedCount) {}

    public record PendingMessage(Long childMessageId, String request, String reason) {
        public static PendingMessage of(ChildMessage m) {
            return new PendingMessage(m.getChildMessageId(), m.getRequest(), m.getReason());
        }
    }

    /** 자녀가 바꾸면 delivery·sentAt, 부모가 답하면 decidedAt이 채워진다 */
    public record StatusResponse(Long childMessageId, ChildMessage.Status status, ChildMessage.Delivery delivery,
                                 LocalDateTime sentAt, LocalDateTime decidedAt) {
        public static StatusResponse of(ChildMessage m) {
            return new StatusResponse(m.getChildMessageId(), m.getStatus(), m.getDelivery(), m.getSentAt(), m.getDecidedAt());
        }
    }

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
