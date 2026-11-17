package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A 브리핑 2026-04~2026-09 (9월만 안 읽음). 가족 B는 성년 전환. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BriefingApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 가장_최근_브리핑의_다섯_줄을_준다() throws Exception {
        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("브리핑 조회 성공"))
                .andExpect(jsonPath("$.data.targetMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.isNew").value(true))
                .andExpect(jsonPath("$.data.lines.spent").value("9월에는 61,500원을 썼어요."))
                .andExpect(jsonPath("$.data.lines.saved").value("결제일에 8,500원을 남겼어요."))
                .andExpect(jsonPath("$.data.lines.changed").value("지난달보다 7,000원 덜 썼어요."))
                .andExpect(jsonPath("$.data.lines.good").value("6개월 연속 결제일에 완납했어요."))
                .andExpect(jsonPath("$.data.lines.together").value("다음 달 친구 생일 선물비를 함께 이야기해 보세요."))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T00:00:00"));
    }

    @Test
    void 브리핑이_없으면_data_null() throws Exception {
        jdbc.update("DELETE FROM briefings WHERE family_id = 1");

        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 브리핑_목록은_최신순으로_첫_줄만_준다() throws Exception {
        mockMvc.perform(get("/briefings").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("브리핑 목록 조회 성공"))
                .andExpect(jsonPath("$.data.totalCount").value(6))
                .andExpect(jsonPath("$.data.briefings.length()").value(6))
                .andExpect(jsonPath("$.data.briefings[0].targetMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.briefings[0].firstLine").value("9월에는 61,500원을 썼어요."))
                .andExpect(jsonPath("$.data.briefings[0].isNew").value(true))
                .andExpect(jsonPath("$.data.briefings[1].targetMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.briefings[1].isNew").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 브리핑_목록은_12개씩_커서로_넘긴다() throws Exception {
        for (int m = 1; m <= 9; m++) {
            jdbc.update("INSERT INTO briefings (family_id, target_month, spent_line) VALUES (1, ?, '썼어요')", "2025-0" + m);
        }
        String body = mockMvc.perform(get("/briefings").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.totalCount").value(15))
                .andExpect(jsonPath("$.data.briefings.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextCursor");
        assertEquals("2025-04", cursor);

        mockMvc.perform(get("/briefings").param("cursor", cursor).header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.briefings.length()").value(3))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 앞_달_브리핑을_나중에_만들어도_최신_브리핑과_목록_첫_항목이_같다() throws Exception {
        jdbc.update("INSERT INTO briefings (family_id, target_month, spent_line) VALUES (1, '2026-03', '3월에는 75,200원을 썼어요.')");

        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.targetMonth").value("2026-09"));
        mockMvc.perform(get("/briefings").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.briefings[0].targetMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.briefings[6].targetMonth").value("2026-03"));
    }

    @Test
    void 읽음_처리하면_새로_도착이_꺼지고_다시_해도_성공한다() throws Exception {
        Long id = jdbc.queryForObject("SELECT briefing_id FROM briefings WHERE family_id = 1 AND target_month = '2026-09'", Long.class);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(patch("/briefings/" + id + "/read").header("X-Persona-Id", 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("브리핑을 읽음 처리했어요."));
        }
        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.isNew").value(false));
    }

    @Test
    void 다른_가족_브리핑은_404_자녀는_403() throws Exception {
        Long id = jdbc.queryForObject("SELECT briefing_id FROM briefings WHERE family_id = 1 AND target_month = '2026-09'", Long.class);
        jdbc.update("INSERT INTO briefings (family_id, target_month) VALUES (2, '2026-09')");
        Long otherFamily = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        mockMvc.perform(patch("/briefings/" + otherFamily + "/read").header("X-Persona-Id", 1)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/briefings/" + id + "/read").header("X-Persona-Id", 2)).andExpect(status().isForbidden());
    }

    @Test
    void 자녀는_403_성년_전환한_가족의_부모도_403() throws Exception {
        mockMvc.perform(get("/briefings").header("X-Persona-Id", 2)).andExpect(status().isForbidden());
        mockMvc.perform(get("/briefings").header("X-Persona-Id", 3)).andExpect(status().isForbidden());
        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 2)).andExpect(status().isForbidden());
        mockMvc.perform(get("/briefings/latest").header("X-Persona-Id", 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기록이 본인에게 넘어갔어요."));
    }
}
