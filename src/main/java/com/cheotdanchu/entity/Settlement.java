package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    private Long settlementId;

    private Long familyId;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status { SCHEDULED, REQUIRED, SHORTFALL, PARTIAL, COMPLETED }
}
