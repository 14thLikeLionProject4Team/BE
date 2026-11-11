package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mission_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public enum ReviewStatus { PENDING, APPROVED, REJECTED }
}
