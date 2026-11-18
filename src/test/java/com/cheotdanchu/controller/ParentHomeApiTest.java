package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 데모 기준일 10/19. 루피(만 18세, 11/2 생일) 가족 A, 뽀로로(만 19세) 가족 B. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParentHomeApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 가족A_부모_홈() throws Exception {
        mockMvc.perform(get("/parent/home").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부모 홈 조회 성공"))
                .andExpect(jsonPath("$.data.today").value("2026-10-19"))
                .andExpect(jsonPath("$.data.childName").value("루피"))
                .andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"))
                .andExpect(jsonPath("$.data.visibilityNotice").value("총액만 보는 중 · 루피에게도 이렇게 보여요"))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(1))
                .andExpect(jsonPath("$.data.praiseChips[0]").value("6개월 연속 결제일 완납"))
                .andExpect(jsonPath("$.data.praiseChips[1]").value("확인서 발급까지 D-14"))
                .andExpect(jsonPath("$.data.isAdultTransitioned").value(false));
    }

    @Test
    void 성년_전환한_가족B는_칭찬_칩이_없다() throws Exception {
        mockMvc.perform(get("/parent/home").header("X-Persona-Id", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.childName").value("뽀로로"))
                .andExpect(jsonPath("$.data.praiseChips.length()").value(0))
                .andExpect(jsonPath("$.data.isAdultTransitioned").value(true));
    }

    @Test
    void 자녀가_호출하면_403() throws Exception {
        mockMvc.perform(get("/parent/home").header("X-Persona-Id", 2))
                .andExpect(status().isForbidden());
    }
}
