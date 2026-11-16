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

import java.time.LocalDateTime;

/**
 * 아이가 부모님께 전하는 말. 대화에서 후보(CANDIDATE)로 생기고, 아이가 고른 것만 부모에게 간다(SENT).
 * CANDIDATE → MONTHLY(월말에 전하기) → SENT 또는 DISCARDED, CANDIDATE → SENT(지금 전하기),
 * SENT → DISCUSS·ACKNOWLEDGED(부모가 답함)
 */
@Entity
@Table(name = "child_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildMessage {

    private static final int TEXT_LIMIT = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long childMessageId;

    @Column(nullable = false)
    private Long familyId;

    /** 출처 메시지. 대화 기록을 지우면 DB가 NULL로 바꾼다 */
    private Long messageId;

    @Column(nullable = false, length = 200)
    private String request;

    @Column(length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Delivery delivery;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime decidedAt;

    /** LLM이 길게 써도 저장이 실패하지 않도록 컬럼 길이(200자)에 맞춰 자른다. 이모지가 반으로 잘리지 않게 글자 단위로 센다 */
    private static String cut(String text) {
        if (text.codePointCount(0, text.length()) <= TEXT_LIMIT) return text;
        return text.substring(0, text.offsetByCodePoints(0, TEXT_LIMIT));
    }

    public static ChildMessage candidate(Long familyId, Long messageId, String request, String reason, LocalDateTime at) {
        ChildMessage m = new ChildMessage();
        m.familyId = familyId;
        m.messageId = messageId;
        m.request = cut(request);
        m.reason = reason == null ? null : cut(reason);
        m.status = Status.CANDIDATE;
        m.createdAt = at;
        return m;
    }

    public enum Status { CANDIDATE, MONTHLY, SENT, DISCUSS, ACKNOWLEDGED, DISCARDED }

    public enum Delivery { NOW, MONTHLY }
}
