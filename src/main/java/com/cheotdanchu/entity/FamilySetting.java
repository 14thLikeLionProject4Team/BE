package com.cheotdanchu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "family_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilySetting {

    @Id
    private Long familyId;

    /** 용돈 보내는 날 (매월 며칠). 컬럼이 TINYINT라 타입을 맞춰 둔다 (안 맞추면 validate가 서버 시작을 막음) */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int allowanceDay;

    private boolean autoTransfer;

    /** 미션 보상 한 달 상한 (용돈의 %) */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int missionCapRate;
}
