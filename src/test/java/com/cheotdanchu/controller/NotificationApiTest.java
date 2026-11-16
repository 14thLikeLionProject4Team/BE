package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 루피 엄마(1) 알림 2개(정산 결과 읽음, 미션 재요청 안 읽음), 루피(2) 0개. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 내_알림만_최신순으로_본다() throws Exception {
        mockMvc.perform(get("/notifications").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 목록 조회 성공"))
                .andExpect(jsonPath("$.data.notifications.length()").value(2))
                .andExpect(jsonPath("$.data.notifications[0].type").value("MISSION_REMIND"))
                .andExpect(jsonPath("$.data.notifications[0].refId").value(5))
                .andExpect(jsonPath("$.data.notifications[0].isRead").value(false))
                .andExpect(jsonPath("$.data.notifications[1].isRead").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

        mockMvc.perform(get("/notifications").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.notifications.length()").value(0));
    }

    @Test
    void 알림은_20개씩_커서로_넘긴다() throws Exception {
        for (int i = 0; i < 21; i++) {
            jdbc.update("INSERT INTO notifications (member_id, type, title, created_at) VALUES (1, 'RISK_PAYMENT', ?, '2026-10-19 10:00:00')", "알림 " + i);
        }
        String body = mockMvc.perform(get("/notifications").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.notifications.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        Number cursor = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextCursor");

        mockMvc.perform(get("/notifications").param("cursor", cursor.toString()).header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.notifications.length()").value(3))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }
}
