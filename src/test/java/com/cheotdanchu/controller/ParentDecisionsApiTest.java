package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A AI 용돈 제안 1(70,000 → 73,000, 물가 4.2%), 지금 전한 말 1건, 제출 5 = 영수증 미션 10/18 제출. 데모 기준일 10/19. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParentDecisionsApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 용돈_제안_전한_말_미션_확인_카드를_순서대로_준다() throws Exception {
        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("결정 카드 조회 성공"))
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.cards[0].type").value("ALLOWANCE_SUGGESTION"))
                .andExpect(jsonPath("$.data.cards[0].refId").value(1))
                .andExpect(jsonPath("$.data.cards[0].title").value("루피 결제 품목 물가가 4.2% 올랐어요"))
                .andExpect(jsonPath("$.data.cards[0].body").value("월 70,000원 → 73,000원 (+3,000원)을 제안해요."))
                .andExpect(jsonPath("$.data.cards[1].type").value("CHILD_MESSAGE"))
                .andExpect(jsonPath("$.data.cards[1].title").value("다음 달 친구 생일 선물비가 조금 더 필요해요"))
                .andExpect(jsonPath("$.data.cards[1].body").value("미리 말해두고 싶어요"))
                .andExpect(jsonPath("$.data.cards[1].label").value("아이가 전한 말"))
                .andExpect(jsonPath("$.data.cards[2].type").value("MISSION_REVIEW"))
                .andExpect(jsonPath("$.data.cards[2].refId").value(5))
                .andExpect(jsonPath("$.data.cards[2].title").value("이번 주 영수증 정리하기"))
                .andExpect(jsonPath("$.data.cards[2].body").value("+3,000원"))
                .andExpect(jsonPath("$.data.cards[2].label").value("어제 제출"));
    }

    @Test
    void 처리할_게_없으면_빈_배열() throws Exception {
        jdbc.update("UPDATE allowance_suggestions SET status = 'KEPT'");
        jdbc.update("UPDATE mission_submissions SET review_status = 'APPROVED' WHERE review_status = 'PENDING'");
        jdbc.update("UPDATE child_messages SET status = 'ACKNOWLEDGED' WHERE status = 'SENT'");

        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.cards.length()").value(0));
    }

    @Test
    void 자녀가_호출하면_403() throws Exception {
        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 2))
                .andExpect(status().isForbidden());
    }
}
