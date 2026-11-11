package com.cheotdanchu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "allowance_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowanceChange {

    @Id
    private Long changeId;

    private Long familyId;

    /** 변경 후 월 용돈 */
    private int amount;

    /** 이 금액이 적용되기 시작하는 날 */
    private LocalDate effectiveFrom;
}
