package com.cheotdanchu.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)는 이번 정산 기간 용돈 70,000원, 미션 상한 30%(21,000원)에 3,000원을 받았다.
 * 가족 B(뽀로로 엄마 id 3)도 상한 30%. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionCapApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    /** 가족의 미션 상한 비율을 DB에서 읽는다. 테스트 트랜잭션 안이라 커밋이 없으니, 바꾼 것을 먼저 DB에 반영한 뒤 읽는다 */
    private int capRateOf(int familyId) {
        em.flush();
        return jdbc.queryForObject("SELECT mission_cap_rate FROM family_settings WHERE family_id = ?", Integer.class, familyId);
    }

    private ResultActions changeCap(int personaId, String body) throws Exception {
        return mockMvc.perform(patch("/family-settings/mission-cap").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createMission(int reward) throws Exception {
        return mockMvc.perform(post("/missions").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"상한 확인\",\"missionType\":\"CHORE\",\"rewardAmount\":" + reward
                        + ",\"startDate\":\"2026-10-19\",\"endDate\":\"2026-11-15\",\"repeatType\":\"ONCE\",\"verifyMethod\":\"DIRECT\"}"));
    }

    @Test
    void 상한_비율을_바꾸면_저장되고_상한_금액이_같이_온다() throws Exception {
        changeCap(1, "{\"capRate\":50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미션 상한을 바꿨어요"))
                .andExpect(jsonPath("$.data.capRate").value(50))
                .andExpect(jsonPath("$.data.capAmount").value(35000));

        assertEquals(50, capRateOf(1));
    }

    @Test
    void 바꾼_상한이_월_보상_합계에_같은_숫자로_보인다() throws Exception {
        changeCap(1, "{\"capRate\":50}").andExpect(status().isOk());

        for (int persona : new int[]{1, 2}) {
            mockMvc.perform(get("/missions/rewards/summary").header("X-Persona-Id", persona))
                    .andExpect(jsonPath("$.data.capRate").value(50))
                    .andExpect(jsonPath("$.data.capAmount").value(35000))
                    .andExpect(jsonPath("$.data.paidAmount").value(3000))
                    .andExpect(jsonPath("$.data.remainingAmount").value(32000));
        }
    }

    @Test
    void 상한을_낮추면_미션_생성이_422가_되고_올리면_열린다() throws Exception {
        // 기본 30%: 남은 한도 18,000원이라 60,000원 미션은 못 만든다
        createMission(60000).andExpect(status().isUnprocessableEntity());

        changeCap(1, "{\"capRate\":100}").andExpect(status().isOk()); // 남은 한도 67,000원
        createMission(60000).andExpect(status().isCreated());

        changeCap(1, "{\"capRate\":4}").andExpect(status().isOk()); // 상한 2,800원 < 이미 받은 3,000원이라 남은 한도 0
        createMission(1000).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 다른_가족의_상한은_바뀌지_않는다() throws Exception {
        changeCap(3, "{\"capRate\":10}").andExpect(status().isOk());

        assertEquals(10, capRateOf(2));
        assertEquals(30, capRateOf(1));
    }

    @Test
    void 범위_밖이거나_빠지면_400() throws Exception {
        changeCap(1, "{\"capRate\":0}").andExpect(status().isBadRequest());
        changeCap(1, "{\"capRate\":101}").andExpect(status().isBadRequest());
        changeCap(1, "{\"capRate\":-5}").andExpect(status().isBadRequest());
        changeCap(1, "{}").andExpect(status().isBadRequest());
        changeCap(1, "{\"capRate\":\"많이\"}").andExpect(status().isBadRequest());

        assertEquals(30, capRateOf(1));
    }

    @Test
    void 경계값_1과_100은_허용한다() throws Exception {
        changeCap(1, "{\"capRate\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.data.capAmount").value(700));
        changeCap(1, "{\"capRate\":100}").andExpect(status().isOk()).andExpect(jsonPath("$.data.capAmount").value(70000));
    }

    @Test
    void 용돈이_없으면_상한_비율은_저장되고_상한_금액은_null이다() throws Exception {
        jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1");

        changeCap(1, "{\"capRate\":50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capRate").value(50))
                .andExpect(jsonPath("$.data.capAmount").value(nullValue()));
        assertEquals(50, capRateOf(1));
    }

    @Test
    void 가족_설정이_없으면_404() throws Exception {
        jdbc.update("DELETE FROM family_settings WHERE family_id = 1");

        changeCap(1, "{\"capRate\":50}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("가족 약속 설정을 찾을 수 없습니다"));
    }

    @Test
    void 자녀가_바꾸면_403() throws Exception {
        changeCap(2, "{\"capRate\":50}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));

        assertEquals(30, capRateOf(1));
    }
}
