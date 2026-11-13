package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long photoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private MissionSubmission submission;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    public static SubmissionPhoto of(MissionSubmission submission, String imageUrl) {
        SubmissionPhoto p = new SubmissionPhoto();
        p.submission = submission;
        p.imageUrl = imageUrl;
        return p;
    }
}
