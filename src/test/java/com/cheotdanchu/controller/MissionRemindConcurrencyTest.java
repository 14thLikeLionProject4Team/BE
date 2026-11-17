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
 * 재요청 버튼을 동시에 여러 번 눌러도 하루 한 번만 알림이 가야 한다. 시드의 제출 5 = 영수증 미션 확인 대기 (재요청 알림은 어제 보냄).
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 끝나면 만든 알림을 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionRemindConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 동시에_재요청해도_알림은_하나만_간다() throws Exception {
        try {
            List<Integer> statuses = remindConcurrently();

            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "성공은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 409).count(), "나머지는 오늘 이미 보냄(409)이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE type = 'MISSION_REMIND' AND ref_id = 5 AND created_at >= '2026-10-19'", Integer.class));
        } finally {
            jdbc.update("DELETE FROM notifications WHERE type = 'MISSION_REMIND' AND ref_id = 5 AND created_at >= '2026-10-19'");
        }
    }

    private List<Integer> remindConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 2))
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
