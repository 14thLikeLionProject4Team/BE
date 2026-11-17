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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가족 A: 루피 엄마(id 1), 루피(id 2). 가족 B: 뽀로로 엄마(id 3), 뽀로로(id 4). 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VisibilityApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions read(int personaId) throws Exception {
        return mockMvc.perform(get("/settings/visibility").header("X-Persona-Id", personaId));
    }

    private ResultActions change(int personaId, String body) throws Exception {
        return mockMvc.perform(patch("/settings/visibility").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 자녀도_부모도_열람_범위를_본다() throws Exception {
        for (int personaId : new int[]{2, 1, 4, 3}) {
            read(personaId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("조회 성공"))
                    .andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"))
                    .andExpect(jsonPath("$.data.label").value("총액만"))
                    .andExpect(jsonPath("$.data.changeableBy").value("PARENT")); // 바꾸는 건 부모
        }
    }

    @Test
    void 가족_설정이_없으면_기본값_총액만이다() throws Exception {
        jdbc.update("DELETE FROM family_settings WHERE family_id = 2");

        read(4).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"));
    }

    @Test
    void 부모가_총액만으로_바꾼다() throws Exception {
        change(1, "{\"visibility\":\"TOTAL_ONLY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("열람 범위를 바꿨어요"))
                .andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"))
                .andExpect(jsonPath("$.data.label").value("총액만"));

        assertEquals("TOTAL_ONLY", jdbc.queryForObject("SELECT visibility FROM family_settings WHERE family_id = 1", String.class));
    }

    @Test
    void 지원하지_않는_값은_400이고_안내_문구가_있다() throws Exception {
        for (String body : new String[]{"{\"visibility\":\"FULL\"}", "{\"visibility\":\"total_only\"}", "{\"visibility\":\"\"}"}) {
            change(1, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("visibility: 현재는 TOTAL_ONLY만 선택할 수 있어요"));
        }
    }

    @Test
    void 값이_없거나_문자열이_아니면_400() throws Exception {
        change(1, "{}").andExpect(status().isBadRequest());
        change(1, "{\"visibility\":null}").andExpect(status().isBadRequest());
        change(1, "{\"visibility\":123}").andExpect(status().isBadRequest());
        change(1, "{\"visibility\":{\"a\":1}}").andExpect(status().isBadRequest());
        change(1, "").andExpect(status().isBadRequest());
        change(1, "not json").andExpect(status().isBadRequest());
    }

    @Test
    void 자녀가_바꾸려_하면_403이다() throws Exception {
        change(2, "{\"visibility\":\"TOTAL_ONLY\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    @Test
    void 가족_설정이_없으면_변경은_404() throws Exception {
        jdbc.update("DELETE FROM family_settings WHERE family_id = 1");

        change(1, "{\"visibility\":\"TOTAL_ONLY\"}").andExpect(status().isNotFound());
    }

    @Test
    void 없는_페르소나는_404_헤더가_없으면_400() throws Exception {
        read(999).andExpect(status().isNotFound());
        change(999, "{\"visibility\":\"TOTAL_ONLY\"}").andExpect(status().isNotFound());
        mockMvc.perform(get("/settings/visibility")).andExpect(status().isBadRequest());
    }

    @Test
    void 다른_화면의_열람_범위와_같은_값이다() throws Exception {
        change(1, "{\"visibility\":\"TOTAL_ONLY\"}").andExpect(status().isOk());

        mockMvc.perform(get("/parent/home").header("X-Persona-Id", 1)).andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"));
        mockMvc.perform(get("/child/home").header("X-Persona-Id", 2)).andExpect(jsonPath("$.data.chips.visibility").value("TOTAL_ONLY"));
        mockMvc.perform(get("/child/my").header("X-Persona-Id", 2)).andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"));
    }
}
