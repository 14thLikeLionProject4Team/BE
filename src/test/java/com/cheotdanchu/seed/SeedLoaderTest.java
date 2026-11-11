package com.cheotdanchu.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private void clearSeededTables() {
        jdbc.update("DELETE FROM card_payments");
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM accounts");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
