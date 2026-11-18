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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * 같은 제안을 동시에 여러 번 "적용"해도(버튼 연타) 한 번만 처리되어야 한다.
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 끝나면 바꾼 데이터를 직접 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AllowanceSuggestionConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_제안을_동시에_적용해도_한_번만_처리된다() throws Exception {
        try {
            List<Integer> statuses = applyConcurrently();

            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "성공은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 409).count(), "나머지는 이미 처리됨(409)이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1 AND effective_from = '2026-11-01'", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", Integer.class));
        } finally {
            jdbc.update("UPDATE allowance_suggestions SET status = 'PENDING' WHERE suggestion_id = 1");
            jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1 AND effective_from = '2026-11-01'");
            jdbc.update("DELETE FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'");
        }
    }

    private List<Integer> applyConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(patch("/allowance/suggestions/1").header("X-Persona-Id", 1)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
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
