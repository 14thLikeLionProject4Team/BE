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
 * 같은 사람이 처음 바꾸는 항목을 동시에 여러 번 바꿔도(버튼 연타) 모두 성공하고 행은 하나만 남아야 한다.
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 끝나면 만든 행을 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSettingConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 처음_바꾸는_항목을_동시에_바꿔도_모두_성공하고_행은_하나다() throws Exception {
        try {
            List<Integer> statuses = turnConcurrently();

            assertEquals(REQUESTS, statuses.stream().filter(s -> s == 200).count(), "모두 성공해야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification_settings WHERE member_id = 2 AND type = 'NEW_MISSION'", Integer.class));
        } finally {
            jdbc.update("DELETE FROM notification_settings WHERE member_id = 2");
        }
    }

    private List<Integer> turnConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(patch("/notification-settings/NEW_MISSION").header("X-Persona-Id", 2)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
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
