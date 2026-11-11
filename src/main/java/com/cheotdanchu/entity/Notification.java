package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(length = 200)
    private String body;

    private Long refId;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Notification of(Member member, Type type, String title, String body, Long refId, LocalDateTime createdAt) {
        Notification n = new Notification();
        n.member = member;
        n.type = type;
        n.title = title;
        n.body = body;
        n.refId = refId;
        n.createdAt = createdAt;
        return n;
    }

    public enum Type {
        SETTLEMENT_RESULT, SHORTFALL_EXPECTED, SHORTFALL_RESOLVED, AUTO_DEDUCT, RISK_PAYMENT, ADULT_TRANSITION,
        ALLOWANCE_CHANGED, NEW_MISSION, MISSION_REJECTED, MISSION_REWARD, MISSION_PROPOSED, MISSION_REMIND
    }
}
