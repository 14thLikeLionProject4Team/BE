package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산 기간(2026-09-26~10-25) 카드 결제 합계는 79,500원이고
 * 식사 29,000 · 선물 22,000 · 카페 13,500 · 교통 9,000 · 문화 6,000원이다. 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4)는 진행 중인 정산이 없다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildCategoryRatioApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions ratio(int personaId, String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get("/child/payments/category-ratio").header("X-Persona-Id", personaId);
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);
        return mockMvc.perform(request);
    }

    /** 루피 가족의 이번 정산 기간 취소(마이너스) 결제를 넣는다. 정산 ID 는 시드 적재 순서에 기대지 않고 조회해서 쓴다 */
    private void insertCanceled(String category, int amount, String paidAt) {
        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end", Long.class);
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                + "VALUES (2, ?, '취소 가맹점', ?, ?, TRUE, ?)", settlementId, category, amount, paidAt);
    }

    @Test
    void 정산_기간_전체의_카테고리별_합계와_비중을_큰_금액순으로_본다() throws Exception {
        ratio(2, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.totalAmount").value(79500))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].category").value("식사"))
                .andExpect(jsonPath("$.data.items[0].amount").value(29000))
                .andExpect(jsonPath("$.data.items[0].ratio").value(36.5))
                .andExpect(jsonPath("$.data.items[1].category").value("선물"))
                .andExpect(jsonPath("$.data.items[1].ratio").value(27.7))
                .andExpect(jsonPath("$.data.items[2].category").value("카페"))
                .andExpect(jsonPath("$.data.items[2].ratio").value(17.0))
                .andExpect(jsonPath("$.data.items[3].category").value("교통"))
                .andExpect(jsonPath("$.data.items[3].ratio").value(11.3))
                .andExpect(jsonPath("$.data.items[4].category").value("문화"))
                .andExpect(jsonPath("$.data.items[4].ratio").value(7.5));
    }

    @Test
    void 전체_합계는_현재_정산의_청구_예정액과_같다() throws Exception {
        long billed = ((Number) JsonPath.read(mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2))
                .andReturn().getResponse().getContentAsString(), "$.data.billedAmount")).longValue();

        ratio(2, null, null).andExpect(jsonPath("$.data.totalAmount").value(billed));
    }

    @Test
    void 고른_기간만_센다() throws Exception {
        String day = jdbc.queryForObject("SELECT DATE_FORMAT(MIN(paid_at), '%Y-%m-%d') FROM card_payments WHERE member_id = 2", String.class);
        List<Map<String, Object>> expected = jdbc.queryForList(
                "SELECT category, SUM(amount) AS total FROM card_payments WHERE member_id = 2 AND DATE(paid_at) = ? "
                        + "GROUP BY category ORDER BY total DESC, category", day);

        String body = ratio(2, day, day).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(expected.size(), ((List<?>) JsonPath.read(body, "$.data.items")).size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).get("category"), JsonPath.read(body, "$.data.items[" + i + "].category"));
            assertEquals(((Number) expected.get(i).get("total")).longValue(),
                    ((Number) JsonPath.read(body, "$.data.items[" + i + "].amount")).longValue());
        }
    }

    @Test
    void 비중의_합은_100에_가깝다() throws Exception {
        String body = ratio(2, null, null).andReturn().getResponse().getContentAsString();
        List<Double> ratios = JsonPath.read(body, "$.data.items[*].ratio");

        assertEquals(100.0, ratios.stream().mapToDouble(Double::doubleValue).sum(), 0.3);
    }

    @Test
    void 취소는_합산하고_전체_합계는_청구_예정액과_계속_같다() throws Exception {
        insertCanceled("식사", -2000, "2026-10-18 12:00:00");

        ratio(2, null, null)
                .andExpect(jsonPath("$.data.totalAmount").value(77500))
                .andExpect(jsonPath("$.data.items[0].category").value("식사"))
                .andExpect(jsonPath("$.data.items[0].amount").value(27000));
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.billedAmount").value(77500));
    }

    @Test
    void 취소로_합계가_0_이하가_된_카테고리는_뺀다() throws Exception {
        insertCanceled("문화", -6000, "2026-10-18 12:00:00"); // 문화 6,000 - 6,000 = 0
        insertCanceled("교통", -9500, "2026-10-18 12:00:00"); // 교통 9,000 - 9,500 = -500

        ratio(2, null, null)
                .andExpect(jsonPath("$.data.totalAmount").value(64500))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[?(@.category == '문화')]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.category == '교통')]").isEmpty());
    }

    @Test
    void 정산_기간의_첫날과_마지막날은_고를_수_있다() throws Exception {
        ratio(2, "2026-09-26", "2026-10-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(79500));
    }

    @Test
    void 정산_기간_밖의_날짜나_거꾸로_고른_기간은_400() throws Exception {
        ratio(2, "2026-09-25", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 기간(2026-09-26~2026-10-25) 안에서 골라주세요"));
        ratio(2, null, "2026-10-26").andExpect(status().isBadRequest());
        ratio(2, "2026-10-20", "2026-10-10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("끝나는 날은 시작일 이후여야 해요"));
        ratio(2, "abc", null).andExpect(status().isBadRequest());
    }

    @Test
    void 진행_중인_정산이_없으면_빈_목록이다() throws Exception {
        ratio(4, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void 부모가_조회하면_403() throws Exception {
        ratio(1, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
        ratio(3, null, null).andExpect(status().isForbidden());
    }
}
