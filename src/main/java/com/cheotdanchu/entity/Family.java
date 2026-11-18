package com.cheotdanchu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "families")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Family {

    @Id
    private Long familyId;

    @Column(nullable = false, length = 20)
    private String name;
}
