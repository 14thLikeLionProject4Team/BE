package com.cheotdanchu.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 서버가 켜질 때 적재가 끝난 개발 DB를 쓰며, 테스트 안에서 적재 테이블을 비웠다가 마지막에 다시 채운다 (MySQL 필요) */
@SpringBootTest
class SeedLoaderTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    SeedLoader seedLoader;

    // 계좌까지 넣은 뒤 정산에서 실패해도 계좌가 남지 않아야 한다 (남으면 다음 기동에서 "이미 적재됨"으로 착각해 건너뛴다)
    @Test
    void 적재_도중_실패하면_이미_넣은_계좌도_되돌려진다() {
        clearSeededTables();
        try {
            SeedLoader broken = new SeedLoader(jdbc, txManager, "seed-test/broken.json");

            assertThrows(IllegalStateException.class, () -> broken.run(null));

            assertEquals(0, count("accounts"));
            assertEquals(0, count("settlements"));
        } finally {
            seedLoader.run(null); // 다른 테스트가 쓰도록 원래 시드로 복구
        }
    }

    // 가족 A: 2025-11 60,000원(처음) → 2026-03 70,000원(대학 입학) → 2026-11 73,000원(물가·AI 제안, 기준일 달 1일에 정함)
    @Test
    void 용돈_변경_이력이_시드대로_들어간다() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT prev_amount, amount, effective_from, reason, source, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS decided "
                        + "FROM allowance_changes WHERE family_id = 1 ORDER BY effective_from");

        assertEquals(3, rows.size());
        assertNull(rows.get(0).get("prev_amount"));
        assertEquals(60000, ((Number) rows.get(0).get("amount")).intValue());
        assertEquals("INITIAL", rows.get(0).get("reason"));

        assertEquals(60000, ((Number) rows.get(1).get("prev_amount")).intValue());
        assertEquals(70000, ((Number) rows.get(1).get("amount")).intValue());
        assertEquals("SCHOOL", rows.get(1).get("reason"));
        assertEquals("PARENT", rows.get(1).get("source"));

        assertEquals(73000, ((Number) rows.get(2).get("amount")).intValue());
        assertEquals("2026-11-01", rows.get(2).get("effective_from").toString());
        assertEquals("INFLATION", rows.get(2).get("reason"));
        assertEquals("AI_SUGGESTION", rows.get(2).get("source"));
        assertEquals("2026-10-01 09:00", rows.get(2).get("decided"));
    }

    @Test
    void 가족_설정이_시드대로_들어간다() {
        // 용돈 보내는 날 1일, 결제일 25일, 미션 상한 30%, 카드 한도 10만 원, 자동이체는 기본값(켜짐)
        Map<String, Object> s = jdbc.queryForMap(
                "SELECT allowance_day, settlement_day, mission_cap_rate, card_limit, auto_transfer "
                        + "FROM family_settings WHERE family_id = 1");

        assertEquals(1, ((Number) s.get("allowance_day")).intValue());
        assertEquals(25, ((Number) s.get("settlement_day")).intValue());
        assertEquals(30, ((Number) s.get("mission_cap_rate")).intValue());
        assertEquals(100000, ((Number) s.get("card_limit")).intValue());
        assertEquals(true, s.get("auto_transfer"));
    }

    private void clearSeededTables() {
        jdbc.update("DELETE FROM card_payments");
        jdbc.update("DELETE FROM transfers");
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM family_settings");
        jdbc.update("DELETE FROM allowance_changes");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
