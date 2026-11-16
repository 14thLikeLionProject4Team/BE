package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 제출을 동시에 여러 번 승인해도(버튼 연타) 보상은 한 번만 나가야 한다. 시드의 제출 5 = 영수증 미션(3,000원) 확인 대기.
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 끝나면 바꾼 데이터를 직접 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionApproveConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_제출을_동시에_승인해도_보상은_한_번만_나간다() throws Exception {
        int balanceBefore = jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class);
        try {
            List<Integer> statuses = approveConcurrently();

            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "성공은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 409).count(), "나머지는 이미 처리됨(409)이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transfers WHERE mission_id = 2 AND type = 'MISSION_REWARD'", Integer.class));
            assertEquals(balanceBefore + 3000, jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class));
        } finally {
            jdbc.update("UPDATE mission_submissions SET review_status = 'PENDING', reviewed_at = NULL WHERE submission_id = 5");
            jdbc.update("UPDATE missions SET status = 'SUBMITTED' WHERE mission_id = 2");
            jdbc.update("DELETE FROM transfers WHERE mission_id = 2 AND type = 'MISSION_REWARD'");
            jdbc.update("UPDATE accounts SET balance = ? WHERE member_id = 2", balanceBefore);
            jdbc.update("DELETE FROM notifications WHERE member_id = 2 AND type = 'MISSION_REWARD'");
        }
    }

    private List<Integer> approveConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 1))
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
