package com.cheotdanchu.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)는 용돈 70,000원·1일·자동이체·결제일 25일에 대기 중인 제안 1번, 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4)는 제안 없음. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllowanceSetupApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions setup(int personaId) throws Exception {
        return mockMvc.perform(get("/allowance/setup").header("X-Persona-Id", personaId));
    }

    @Test
    void 부모는_현재_설정과_대기_중인_제안_참고_구간_이유_선택지를_본다() throws Exception {
        setup(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.currentAmount").value(70000))
                .andExpect(jsonPath("$.data.allowanceDay").value(1))
                .andExpect(jsonPath("$.data.autoTransfer").value(true))
                .andExpect(jsonPath("$.data.settlementDay").value(25))
                .andExpect(jsonPath("$.data.referenceRange.min").value(70000))
                .andExpect(jsonPath("$.data.referenceRange.max").value(75000))
                .andExpect(jsonPath("$.data.pendingSuggestion.suggestionId").value(1))
                .andExpect(jsonPath("$.data.pendingSuggestion.suggestedAmount").value(73000))
                .andExpect(jsonPath("$.data.pendingSuggestion.explanation").value("루피가 자주 결제하는 품목의 물가가 평균 4.2% 올랐어요."))
                .andExpect(jsonPath("$.data.reasons.length()").value(4))
                .andExpect(jsonPath("$.data.reasons[0].code").value("INFLATION"))
                .andExpect(jsonPath("$.data.reasons[0].label").value("물가"))
                .andExpect(jsonPath("$.data.reasons[1].label").value("진학"))
                .andExpect(jsonPath("$.data.reasons[2].label").value("다시 약속"))
                .andExpect(jsonPath("$.data.reasons[3].code").value("ETC"));
    }

    @Test
    void 대기_중인_제안이_없으면_제안과_참고_구간은_null이다() throws Exception {
        setup(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingSuggestion").value(nullValue()))
                .andExpect(jsonPath("$.data.referenceRange").value(nullValue()))
                .andExpect(jsonPath("$.data.reasons.length()").value(4));
    }

    @Test
    void 제안을_처리하면_배너는_사라지고_참고_구간은_남는다() throws Exception {
        mockMvc.perform(patch("/allowance/suggestions/1").header("X-Persona-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"KEPT\"}")).andExpect(status().isOk());

        setup(1)
                .andExpect(jsonPath("$.data.pendingSuggestion").value(nullValue()))
                .andExpect(jsonPath("$.data.referenceRange.min").value(70000))
                .andExpect(jsonPath("$.data.referenceRange.max").value(75000));
    }

    @Test
    void 용돈_이력이_없으면_현재_금액은_null이다() throws Exception {
        jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1");

        setup(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.allowanceDay").value(1));
    }

    @Test
    void 가족_설정이_없으면_404() throws Exception {
        jdbc.update("DELETE FROM family_settings WHERE family_id = 1");

        setup(1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("가족 약속 설정을 찾을 수 없습니다"));
    }

    @Test
    void 저장한_보내는_날과_결제일이_바로_보이고_정한_금액은_다음_달부터라_현재_금액은_그대로다() throws Exception {
        mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":72000,\"allowanceDay\":5,\"autoTransfer\":false,\"settlementDay\":20}")).andExpect(status().isCreated());

        setup(1)
                .andExpect(jsonPath("$.data.currentAmount").value(70000))
                .andExpect(jsonPath("$.data.allowanceDay").value(5))
                .andExpect(jsonPath("$.data.autoTransfer").value(false))
                .andExpect(jsonPath("$.data.settlementDay").value(20));
    }

    @Test
    void childId를_주면_우리_가족의_아이만_허용한다() throws Exception {
        mockMvc.perform(get("/allowance/setup").param("childId", "2").header("X-Persona-Id", 1)).andExpect(status().isOk());
        mockMvc.perform(get("/allowance/setup").param("childId", "4").header("X-Persona-Id", 1)).andExpect(status().isNotFound());
        mockMvc.perform(get("/allowance/setup").param("childId", "1").header("X-Persona-Id", 1)).andExpect(status().isNotFound());
        mockMvc.perform(get("/allowance/setup").param("childId", "999").header("X-Persona-Id", 1)).andExpect(status().isNotFound());
    }

    @Test
    void 자녀가_조회하면_403() throws Exception {
        setup(2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }
}
