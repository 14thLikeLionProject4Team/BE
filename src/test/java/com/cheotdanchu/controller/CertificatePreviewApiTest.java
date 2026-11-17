package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가족 A 루피(id 2)는 발급 요건을 아직 못 채웠어도 미리보기를 본다. 가족 B 뽀로로(id 4)는 27개월 기록. 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificatePreviewApiTest {

    @Autowired
    MockMvc mockMvc;

    private ResultActions preview(int personaId) throws Exception {
        return mockMvc.perform(get("/child/certificates/preview").header("X-Persona-Id", personaId));
    }

    @Test
    void 가족A는_발급_전에도_명세_예시와_같은_지표를_본다() throws Exception {
        preview(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.fullPaidMonths").value(10))
                .andExpect(jsonPath("$.data.totalMonths").value(11))
                .andExpect(jsonPath("$.data.maxStreak").value(6))
                .andExpect(jsonPath("$.data.avgMarginRate").value(7.5))
                .andExpect(jsonPath("$.data.selfResolvedCount").value(0))
                .andExpect(jsonPath("$.data.shortfallCount").value(1));
    }

    @Test
    void 가족B는_자기_가족의_기록으로_계산된다() throws Exception {
        preview(4)
                .andExpect(jsonPath("$.data.fullPaidMonths").value(24))
                .andExpect(jsonPath("$.data.totalMonths").value(27))
                .andExpect(jsonPath("$.data.maxStreak").value(15))
                .andExpect(jsonPath("$.data.selfResolvedCount").value(3))
                .andExpect(jsonPath("$.data.shortfallCount").value(3));
    }

    @Test
    void 기록_지표_조회와_같은_값이다() throws Exception {
        String records = mockMvc.perform(get("/records/summary").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();
        String preview = preview(2).andReturn().getResponse().getContentAsString();

        assertEquals(records, preview);
    }

    @Test
    void 부모가_조회하면_403() throws Exception {
        preview(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
