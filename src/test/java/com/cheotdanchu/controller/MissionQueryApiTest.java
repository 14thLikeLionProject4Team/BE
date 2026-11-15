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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 4 = 아이가 제안한 화분 미션, 제출 5 = 영수증 미션 확인 대기. 가족 A 10월 한도 21,000원 중 3,000원 받음. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionQueryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private void insertMission(String title, String status) {
        jdbc.update("INSERT INTO missions (family_id, title, mission_type, reward_amount, end_date, repeat_type, origin, status) "
                + "VALUES (1, ?, 'CHORE', 1000, '2026-10-10', 'ONCE', 'CHILD', ?)", title, status);
    }

    @Test
    void 부모는_결정하지_않은_제안만_보고_자녀는_보류된_제안도_본다() throws Exception {
        insertMission("보류된 제안", "ON_HOLD");

        mockMvc.perform(get("/missions").param("status", "PROPOSED").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("제안 미션 목록 조회 성공"))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.missions[0].missionId").value(4))
                .andExpect(jsonPath("$.data.missions[0].desiredReward").value(3000))
                .andExpect(jsonPath("$.data.missions[0].criteria").value("일주일에 한 번 베란다 화분에 물 주기"));

        mockMvc.perform(get("/missions").param("status", "PROPOSED").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void 승인으로_끝난_미션은_종료_목록에_지급액과_함께_나온다() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 1)).andExpect(status().isOk());

        mockMvc.perform(get("/missions").param("status", "ended").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missions[0].missionId").value(2))
                .andExpect(jsonPath("$.data.missions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.missions[0].paidTotal").value(3000))
                .andExpect(jsonPath("$.data.missions[0].endedAt").value("2026-10-19"))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 종료_목록은_20개씩_커서로_넘긴다() throws Exception {
        for (int i = 0; i < 22; i++) insertMission("취소된 미션 " + i, "CANCELED");

        String body = mockMvc.perform(get("/missions").param("status", "ended").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missions.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        Number cursor = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextCursor");

        mockMvc.perform(get("/missions").param("status", "ended").param("cursor", cursor.toString()).header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missions.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 승인_대기_목록은_자녀와_부모가_같이_본다() throws Exception {
        for (long persona : new long[]{1, 2}) {
            mockMvc.perform(get("/missions/submissions").param("status", "PENDING").header("X-Persona-Id", persona))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(1))
                    .andExpect(jsonPath("$.data.submissions[0].submissionId").value(5))
                    .andExpect(jsonPath("$.data.submissions[0].missionTitle").value("이번 주 영수증 정리하기"))
                    .andExpect(jsonPath("$.data.submissions[0].rewardAmount").value(3000));
        }
    }

    @Test
    void 월_보상_합계는_자녀와_부모가_같은_숫자를_본다() throws Exception {
        for (long persona : new long[]{1, 2}) {
            mockMvc.perform(get("/missions/rewards/summary").header("X-Persona-Id", persona))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.month").value("2026-10"))
                    .andExpect(jsonPath("$.data.paidAmount").value(3000))
                    .andExpect(jsonPath("$.data.capAmount").value(21000))
                    .andExpect(jsonPath("$.data.capRate").value(30))
                    .andExpect(jsonPath("$.data.remainingAmount").value(18000));
        }
    }

    @Test
    void 지원하지_않는_상태는_400() throws Exception {
        mockMvc.perform(get("/missions").param("status", "foo").header("X-Persona-Id", 1))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/missions/submissions").param("status", "APPROVED").header("X-Persona-Id", 1))
                .andExpect(status().isBadRequest());
    }
}
