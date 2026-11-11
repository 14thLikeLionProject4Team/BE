package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 서버가 켜질 때 schema.sql·data.sql·SeedLoader 가 실행된 개발 DB를 그대로 쓴다 (MySQL 필요) */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementCheckApiTest {

    @Autowired
    MockMvc mockMvc;

    // 루피(가족 A 자녀, id 2): 기준일 10/19 = 결제일 D-6, 청구 79,500원 / 쓸 수 있는 돈 73,000원 / 남은 돈 59,900원
    @Test
    void 가족A_자녀는_부족_예상_점검_카드를_받는다() throws Exception {
        mockMvc.perform(get("/settlements/current/check").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부족 예상 조회 성공"))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-25"))
                .andExpect(jsonPath("$.data.dDay").value(6))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.availableAmount").value(73000))
                .andExpect(jsonPath("$.data.carryoverAmount").value(59900))
                .andExpect(jsonPath("$.data.shortfallAmount").value(6500))
                .andExpect(jsonPath("$.data.message").value("지금 속도면 결제일에 6,500원이 모자랄 것 같아요"))
                .andExpect(jsonPath("$.data.guides[0]").value("지난달까지 남은 돈 59,900원으로 갚을 수 있어요"))
                .andExpect(jsonPath("$.data.guides[1]").value("다음 용돈 전까지 직접 보내도 돼요"))
                .andExpect(jsonPath("$.data.guides[2]").value("다른 사람에게 빌리지 마세요"));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/settlements/current/check").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }

    // 뽀로로(가족 B 자녀, id 4): 진행 중인 정산이 없다. 에러가 아니라 visible=false (남은 돈 258,100원, 쓸 수 있는 돈 0원)
    @Test
    void 진행_중인_정산이_없는_가족B는_visible이_false인_200() throws Exception {
        mockMvc.perform(get("/settlements/current/check").header("X-Persona-Id", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.data.dDay").value(nullValue()))
                .andExpect(jsonPath("$.data.billedAmount").value(0))
                .andExpect(jsonPath("$.data.availableAmount").value(0))
                .andExpect(jsonPath("$.data.carryoverAmount").value(258100))
                .andExpect(jsonPath("$.data.shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.message").value(nullValue()))
                .andExpect(jsonPath("$.data.guides").isEmpty());
    }

    @Test
    void 헤더가_없으면_400() throws Exception {
        mockMvc.perform(get("/settlements/current/check"))
                .andExpect(status().isBadRequest());
    }
}
