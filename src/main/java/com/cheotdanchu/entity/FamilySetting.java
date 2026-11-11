package com.cheotdanchu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "family_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilySetting {

    @Id
    private Long familyId;

    /** 용돈 보내는 날 (매월 며칠) */
    private int allowanceDay;

    private boolean autoTransfer;
}
