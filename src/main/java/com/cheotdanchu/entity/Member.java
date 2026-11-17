package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    private static final int ADULT_AGE = 19;

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

    /** 만 19세가 되는 날. 이날부터 기록이 본인에게 넘어간다 (부모에게는 성년 전환 표시만) */
    public LocalDate adultDay() {
        return birthDate.plusYears(ADULT_AGE);
    }

    public boolean isAdultOn(LocalDate today) {
        return !adultDay().isAfter(today);
    }


    /**
     * 만 나이. 윤년 2월 29일생은 평년에 2월 28일에 한 살을 먹는 것으로 보아, 성년 전환일(adultDay)과 어긋나지 않게 한다
     * (Period 만 쓰면 2월 28일에 성년 전환이 됐는데도 만 18세로 나온다).
     */
    public int ageOn(LocalDate today) {
        int age = Period.between(birthDate, today).getYears();
        return birthDate.plusYears(age + 1L).isAfter(today) ? age : age + 1;
    }

    public enum Role { PARENT, CHILD }
}