package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 부족분을 동시에 여러 번 갚아도(버튼 연타) 한 번만 갚아지고 남은 돈도 한 번만 줄어야 한다.
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 이번 정산을 결제일이 지난 부족 정산(청구 79,500원 중 70,000원 이체,
 * 부족 9,500원)으로 바꿔서 쓰고 끝나면 바꾼 데이터를 직접 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortfallPaymentConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_부족분을_동시에_갚아도_한_번만_갚아진다() throws Exception {
        long settlementId = jdbc.queryForObject("SELECT settlement_id FROM settlements WHERE family_id = 1 AND period_end = '2026-10-25'", Long.class);
        Map<String, Object> before = jdbc.queryForMap("SELECT balance, carryover_balance FROM accounts WHERE member_id = 2");
        jdbc.update("UPDATE settlements SET status = 'SHORTFALL', billed_amount = 79500 WHERE settlement_id = ?", settlementId);
        jdbc.update("INSERT INTO transfers (from_account_id, to_account_id, type, amount, settlement_id, transferred_at) "
                + "SELECT c.account_id, p.account_id, 'SETTLEMENT', 70000, ?, '2026-10-18 18:00:00' "
                + "FROM accounts c, accounts p WHERE c.member_id = 2 AND p.member_id = 1", settlementId);
        try {
            List<Integer> statuses = payConcurrently(settlementId);

            // 부족 9,500원을 한 번에 갚는 요청이 5개: 하나만 갚아지고 나머지는 갚을 부족분이 없다
            assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "갚아진 요청은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 409).count(), "나머지는 갚을 부족분이 없음(409)이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transfers WHERE settlement_id = ? AND type = 'SHORTFALL_PAYMENT'", Integer.class, settlementId));
            assertEquals(((Number) before.get("carryover_balance")).intValue() - 9500,
                    jdbc.queryForObject("SELECT carryover_balance FROM accounts WHERE member_id = 2", Integer.class));
            assertEquals(((Number) before.get("balance")).intValue() - 9500,
                    jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class));
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM settlements WHERE settlement_id = ?", String.class, settlementId));
        } finally {
            jdbc.update("DELETE FROM transfers WHERE settlement_id = ?", settlementId);
            jdbc.update("UPDATE settlements SET status = 'SCHEDULED', result = NULL, billed_amount = NULL, resolved_at = NULL WHERE settlement_id = ?", settlementId);
            jdbc.update("UPDATE accounts SET balance = ?, carryover_balance = ? WHERE member_id = 2",
                    before.get("balance"), before.get("carryover_balance"));
        }
    }

    private List<Integer> payConcurrently(long settlementId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(post("/settlements/" + settlementId + "/shortfall-payments").header("X-Persona-Id", 2)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"CARRYOVER\",\"amount\":9500}"))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(request));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) statuses.add(f.get());
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
