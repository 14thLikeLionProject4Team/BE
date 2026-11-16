package com.cheotdanchu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/** 자녀 대화창의 메시지 한 줄 (자녀가 보낸 말, 첫단추 답). 부모는 볼 수 없다 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType cardType;

    /** 카드 데이터 (금융 설명이면 inScope·출처). 일반 답이면 null */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ChatMessage fromChild(Long memberId, String content, LocalDateTime at) {
        return of(memberId, Sender.CHILD, content, CardType.TEXT, null, at);
    }

    public static ChatMessage fromBot(Long memberId, String content, CardType cardType, Map<String, Object> payload, LocalDateTime at) {
        return of(memberId, Sender.BOT, content, cardType, payload, at);
    }

    private static ChatMessage of(Long memberId, Sender sender, String content, CardType cardType, Map<String, Object> payload, LocalDateTime at) {
        ChatMessage m = new ChatMessage();
        m.memberId = memberId;
        m.sender = sender;
        m.content = content;
        m.cardType = cardType;
        m.payload = payload;
        m.createdAt = at;
        return m;
    }

    public enum Sender { CHILD, BOT }

    /** TEXT 일반 답, NUMBER 내 숫자, RAG 금융 설명, RETROSPECT 월말 회고 */
    public enum CardType { TEXT, NUMBER, RAG, RETROSPECT }
}
