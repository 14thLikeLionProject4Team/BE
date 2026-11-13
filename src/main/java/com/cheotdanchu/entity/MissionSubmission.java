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

    @Enumerated(EnumType.STRING)
    private RejectReason rejectReason;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    public static MissionSubmission of(Mission mission, LocalDateTime submittedAt) {
        MissionSubmission s = new MissionSubmission();
        s.mission = mission;
        s.reviewStatus = ReviewStatus.PENDING;
        s.submittedAt = submittedAt;
        return s;
    }

    public boolean isPending() {
        return reviewStatus == ReviewStatus.PENDING;
    }

    public void approve(LocalDateTime at) {
        reviewStatus = ReviewStatus.APPROVED;
        reviewedAt = at;
    }

    public void reject(RejectReason reason, LocalDateTime at) {
        reviewStatus = ReviewStatus.REJECTED;
        rejectReason = reason;
        reviewedAt = at;
    }

    public enum ReviewStatus { PENDING, APPROVED, REJECTED }

    public enum RejectReason {
        PHOTO_UNCLEAR("사진이 잘 안 보여요"),
        NOT_DONE("아직 다 안 한 것 같아요");

        public final String label;

        RejectReason(String label) {
            this.label = label;
        }
    }
}
