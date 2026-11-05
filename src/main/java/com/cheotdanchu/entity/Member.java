package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)   // 구성원 여러 명(Many) → 가족 하나(One)
    @JoinColumn(name = "family_id", nullable = false)      // DB에서는 이 컬럼으로 연결
    private Family family;

    @Enumerated(EnumType.STRING)   // DB에 'PARENT' 같은 문자열로 저장
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(length = 50)
    private String relation;

    public enum Role { PARENT, CHILD }
}