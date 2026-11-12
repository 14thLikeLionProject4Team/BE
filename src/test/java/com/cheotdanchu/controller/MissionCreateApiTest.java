package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 서버가 켜질 때 schema.sql·data.sql·SeedLoader 가 실행된 DB를 그대로 쓴다 (MySQL 필요). 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionCreateApiTest {

    @Autowired
    MockMvc mockMvc;

    // 가족 A 한도: 10월 용돈 70,000원 × 30% = 21,000원, 현재 정산 기간 중 10/2에 받은 미션 보상 3,000원 → 남은 한도 18,000원
    private String body(int reward) {
        return """
                {"title":"베란다 화분 물 주기","missionType":"CHORE","rewardAmount":%d,
                 "startDate":"2026-10-19","endDate":"2026-11-15","repeatType":"WEEKLY","verifyMethod":"PHOTO"}
                """.formatted(reward);
    }

    @Test
    void 남은_한도까지는_만들_수_있다() throws Exception {
        mockMvc.perform(post("/missions").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(body(18000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    @Test
    void 남은_한도를_넘으면_422() throws Exception {
        mockMvc.perform(post("/missions").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(body(18001)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이번 달 남은 한도(18,000원)를 넘어요."));
    }

    @Test
    void 자녀가_만들면_403() throws Exception {
        mockMvc.perform(post("/missions").header("X-Persona-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(body(1000)))
                .andExpect(status().isForbidden());
    }
}
