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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 용돈을 동시에 여러 번 저장해도(버튼 연타) 변경 이력과 아이 알림은 한 번만 생겨야 한다.
 * 요청마다 트랜잭션이 따로 있어야 해서 롤백되는 테스트가 아니고, 끝나면 만든 데이터를 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AllowanceChangeConcurrencyTest {

    private static final int REQUESTS = 5;
    // 시드와 같은 보내는 날(1)·자동이체(true)·결제일(25)로 보내서, 끝나고 되돌릴 설정이 없다
    private static final String BODY = "{\"amount\":72000,\"reason\":\"SCHOOL\",\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_저장을_동시에_보내도_이력과_알림은_한_번만_생긴다() throws Exception {
        try {
            List<Integer> statuses = saveConcurrently();

            assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "새 이력이 생긴 요청은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 200).count(), "나머지는 바뀐 게 없어서 200이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1 AND effective_from = '2026-11-01'", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", Integer.class));
        } finally {
            jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1 AND effective_from = '2026-11-01'");
            jdbc.update("DELETE FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'");
        }
    }

    private List<Integer> saveConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1)
                                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
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
