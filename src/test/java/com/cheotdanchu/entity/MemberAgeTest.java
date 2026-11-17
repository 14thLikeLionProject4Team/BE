package com.cheotdanchu.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberAgeTest {

    private static Member bornOn(String birthDate) {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "birthDate", LocalDate.parse(birthDate));
        return member;
    }

    @Test
    void 생일_전날까지는_한_살_적다() {
        Member m = bornOn("2007-11-02");

        assertEquals(18, m.ageOn(LocalDate.parse("2026-11-01")));
        assertEquals(19, m.ageOn(LocalDate.parse("2026-11-02")));
    }

    @Test
    void 평년의_2월_29일생은_2월_28일에_한_살을_먹고_성년_전환과_같은_날이다() {
        Member m = bornOn("2008-02-29"); // 2027 은 평년

        assertEquals(18, m.ageOn(LocalDate.parse("2027-02-27")));
        assertFalse(m.isAdultOn(LocalDate.parse("2027-02-27")));
        assertEquals(19, m.ageOn(LocalDate.parse("2027-02-28")));
        assertTrue(m.isAdultOn(LocalDate.parse("2027-02-28"))); // adultDay 와 만 나이가 같은 날 바뀐다
    }

    @Test
    void 윤년의_2월_29일생은_2월_29일에_한_살을_먹는다() {
        Member m = bornOn("2008-02-29"); // 2028 은 윤년

        assertEquals(19, m.ageOn(LocalDate.parse("2028-02-28"))); // 이미 2027-02-28 에 19세
        assertEquals(20, m.ageOn(LocalDate.parse("2028-02-29")));
    }

    @Test
    void 성년_전환_여부와_만_19세_이상이_언제나_일치한다() {
        Member m = bornOn("2008-02-29");
        for (LocalDate day = LocalDate.parse("2027-02-20"); day.isBefore(LocalDate.parse("2027-03-05")); day = day.plusDays(1)) {
            assertEquals(m.isAdultOn(day), m.ageOn(day) >= 19, day.toString());
        }
    }
}
