package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 서버가 켜질 때 schema.sql·data.sql·SeedLoader 가 실행된 개발 DB를 그대로 쓴다 (MySQL 필요) */
@SpringBootTest
@AutoConfigureMockMvc
class ParentChildSpendingApiTest {

    @Autowired
    MockMvc mockMvc;

    // 루피 엄마(가족 A 부모, id 1): 기준일 10/19 = 결제일 D-6, 이번 정산 기간 사용 79,500원, 10월 용돈 70,000원 자동이체로 보냄
    @Test
    void 가족A_부모는_정산_요약을_받는다() throws Exception {
        mockMvc.perform(get("/parent/child-spending").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("정산 요약 조회 성공"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-25"))
                .andExpect(jsonPath("$.data.dDay").value(6))
                .andExpect(jsonPath("$.data.totalSpent").value(79500))
                .andExpect(jsonPath("$.data.allowance.month").value("2026-10"))
                .andExpect(jsonPath("$.data.allowance.amount").value(70000))
                .andExpect(jsonPath("$.data.allowance.autoTransfer").value(true))
                .andExpect(jsonPath("$.data.allowance.sent").value(true))
                .andExpect(jsonPath("$.data.notice").value("결과는 결제일에 알려드려요"));
    }

    // 부족 예상(D-6)은 아이에게 먼저 가고 부모 홈에는 뜨지 않는 것이 정상이다
    @Test
    void 부모에게는_부족_예상과_결제_목록을_내려주지_않는다() throws Exception {
        mockMvc.perform(get("/parent/child-spending").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortfallAmount").doesNotExist())
                .andExpect(jsonPath("$.data.availableAmount").doesNotExist())
                .andExpect(jsonPath("$.data.carryoverAmount").doesNotExist())
                .andExpect(jsonPath("$.data.payments").doesNotExist());
    }

    @Test
    void 자녀가_호출하면_403() throws Exception {
        mockMvc.perform(get("/parent/child-spending").header("X-Persona-Id", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    // 뽀로로 엄마(가족 B 부모, id 3): 아이(뽀로로)가 10/3 에 만 19세가 되어 기록이 본인에게 넘어갔다
    @Test
    void 성년_전환_후_부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/parent/child-spending").header("X-Persona-Id", 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기록이 본인에게 넘어갔어요."));
    }

    @Test
    void 없는_페르소나는_404() throws Exception {
        mockMvc.perform(get("/parent/child-spending").header("X-Persona-Id", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void 헤더가_없으면_400() throws Exception {
        mockMvc.perform(get("/parent/child-spending"))
                .andExpect(status().isBadRequest());
    }
}
