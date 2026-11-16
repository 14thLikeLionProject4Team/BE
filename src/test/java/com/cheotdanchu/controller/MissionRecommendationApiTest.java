package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
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

/** 시드 기준: 가족 A 추천 미션 자녀 화면 5·6·7, 부모 화면 8·9·10. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionRecommendationApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 자녀는_자녀_화면_추천만_본다() throws Exception {
        mockMvc.perform(get("/missions/recommendations").param("target", "CHILD").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 미션 목록 조회 성공"))
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                .andExpect(jsonPath("$.data.recommendations[0].missionId").value(5))
                .andExpect(jsonPath("$.data.recommendations[0].title").value("가격 비교 장보기"))
                .andExpect(jsonPath("$.data.recommendations[0].missionType").value("FINANCE"))
                .andExpect(jsonPath("$.data.recommendations[0].rewardAmount").value(3500))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 부모는_부모_화면_추천만_본다() throws Exception {
        mockMvc.perform(get("/missions/recommendations").param("target", "PARENT").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                .andExpect(jsonPath("$.data.recommendations[0].missionId").value(8));
    }

    @Test
    void 열린_추천은_목록에서_빠진다() throws Exception {
        jdbc.update("UPDATE missions SET status = 'NEW' WHERE mission_id = 8");

        mockMvc.perform(get("/missions/recommendations").param("target", "PARENT").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.recommendations.length()").value(2))
                .andExpect(jsonPath("$.data.recommendations[0].missionId").value(9));
    }

    @Test
    void 추천은_10개씩_커서로_넘긴다() throws Exception {
        for (int i = 0; i < 9; i++) {
            jdbc.update("INSERT INTO missions (family_id, title, origin, recommend_to, status) VALUES (1, ?, 'AI', 'CHILD', 'RECOMMENDED')", "추천 " + i);
        }
        String body = mockMvc.perform(get("/missions/recommendations").param("target", "CHILD").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.recommendations.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(body, "$.data.nextCursor");

        mockMvc.perform(get("/missions/recommendations").param("target", "CHILD").param("cursor", cursor.toString()).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.recommendations.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void target이_없거나_내_역할과_다르면_400() throws Exception {
        mockMvc.perform(get("/missions/recommendations").header("X-Persona-Id", 2)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/missions/recommendations").param("target", "MAYBE").header("X-Persona-Id", 2)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/missions/recommendations").param("target", "PARENT").header("X-Persona-Id", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내 화면의 추천만 볼 수 있어요."));
    }

    @Test
    void 추천_미션은_진행_제안_종료_목록에_나오지_않는다() throws Exception {
        mockMvc.perform(get("/missions").param("status", "active").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.missions[?(@.missionId >= 5)]").isEmpty());
        mockMvc.perform(get("/missions").param("status", "PROPOSED").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.missions[?(@.missionId >= 5)]").isEmpty());
    }
}
