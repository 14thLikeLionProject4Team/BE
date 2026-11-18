package com.cheotdanchu.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionTest {

    private Mission mission(Mission.RepeatType repeat, String start, String end) {
        Mission m = new Mission();
        ReflectionTestUtils.setField(m, "repeatType", repeat);
        ReflectionTestUtils.setField(m, "startDate", start == null ? null : LocalDate.parse(start));
        ReflectionTestUtils.setField(m, "endDate", end == null ? null : LocalDate.parse(end));
        return m;
    }

    @Test
    void targetCount() {
        assertEquals(5, mission(Mission.RepeatType.WEEKLY, "2026-10-01", "2026-10-31").targetCount());
        assertEquals(7, mission(Mission.RepeatType.DAILY, "2026-10-19", "2026-10-25").targetCount());
        assertEquals(1, mission(Mission.RepeatType.ONCE, "2026-10-12", "2026-10-18").targetCount());
        assertEquals(3, mission(Mission.RepeatType.MONTHLY, "2026-09-01", "2026-11-30").targetCount());
        assertEquals(1, mission(Mission.RepeatType.WEEKLY, null, null).targetCount());
    }
}
