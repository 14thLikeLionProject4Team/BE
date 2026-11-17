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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준 (정산 목록)
 * - 가족 A(루피 엄마 id 1, 루피 id 2): 끝난 정산 11건(2025-11 ~ 2026-09)과 정산 전인 이번 달 1건. 2026-03 정산은 5,200원이 부족해서 다음 용돈에서 자동 정산됐다.
 * - 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4): 끝난 정산 27건. 부족분을 직접 보낸 달(2024-10)과 남은 돈으로 갚은 달(2025-01·2025-06)이 있다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementHistoryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions list(int personaId, Integer size, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/settlements").header("X-Persona-Id", personaId);
        if (size != null) request.param("size", size.toString());
        if (cursor != null) request.param("cursor", cursor);
        return mockMvc.perform(request);
    }


    /** 가족의 정산 ID 를 종료일로 찾는다 (시드 적재 순서에 기대지 않는다) */
    private long settlementOf(int familyId, String periodEnd) {
        return jdbc.queryForObject("SELECT settlement_id FROM settlements WHERE family_id = ? AND period_end = ?", Long.class, familyId, periodEnd);
    }

    private List<Long> expectedIds(int familyId) {
        return jdbc.queryForList("SELECT settlement_id FROM settlements WHERE family_id = ? AND status <> 'SCHEDULED' ORDER BY period_end DESC",
                Long.class, familyId);
    }

    /** 첫 페이지부터 nextCursor 를 따라 끝까지 읽은 정산 ID 순서 */
    private List<Long> readAllPages(int personaId, Integer size) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            String body = list(personaId, size, cursor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.data.items[*].settlementId");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.data.nextCursor");
            if (cursor == null) return ids;
        }
        throw new AssertionError("페이지가 끝나지 않아요");
    }

    // ---- 목록 ----

    @Test
    void 끝난_정산을_최신순으로_보고_정산_전인_이번_달은_넣지_않는다() throws Exception {
        list(2, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.totalCount").value(11))
                .andExpect(jsonPath("$.data.items.length()").value(11))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].periodStart").value("2026-08-26"))
                .andExpect(jsonPath("$.data.items[0].periodEnd").value("2026-09-25"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].result").value("FULL_PAID"))
                .andExpect(jsonPath("$.data.items[0].billedAmount").value(61500))
                .andExpect(jsonPath("$.data.items[0].isExcluded").value(false));
        assertEquals(expectedIds(1), readAllPages(2, null));
    }

    @Test
    void 자동_정산된_달은_결과가_AUTO_DEDUCTED로_나온다() throws Exception {
        long autoDeducted = settlementOf(1, "2026-03-25");

        String body = list(2, null, null).andReturn().getResponse().getContentAsString();
        List<Object> hit = JsonPath.read(body, "$.data.items[?(@.settlementId == " + autoDeducted + ")].result");
        assertEquals(List.of("AUTO_DEDUCTED"), hit);
    }

    @Test
    void 기록_지표에서_빠진_정산은_isExcluded가_true다() throws Exception {
        long id = settlementOf(1, "2026-08-25");
        jdbc.update("UPDATE settlements SET is_excluded = TRUE WHERE settlement_id = ?", id);

        String body = list(2, null, null).andReturn().getResponse().getContentAsString();
        List<Boolean> excluded = JsonPath.read(body, "$.data.items[?(@.settlementId == " + id + ")].isExcluded");
        assertEquals(List.of(true), excluded);
    }

    @Test
    void 정산이_스물일곱_개인_가족은_기본_스무_개씩_넘기고_빠지거나_겹치지_않는다() throws Exception {
        list(4, null, null)
                .andExpect(jsonPath("$.data.totalCount").value(27))
                .andExpect(jsonPath("$.data.items.length()").value(20));

        for (Integer size : new Integer[]{null, 1, 5, 10, 27}) {
            assertEquals(expectedIds(2), readAllPages(4, size), "size=" + size);
        }
    }

    @Test
    void 한_페이지가_가득_차도_다음이_없으면_nextCursor는_null이다() throws Exception {
        list(2, 11, null)
                .andExpect(jsonPath("$.data.items.length()").value(11))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
        list(2, 10, null)
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(11));
    }

    @Test
    void 잘못된_크기와_깨진_커서는_400이다() throws Exception {
        list(2, 0, null).andExpect(status().isBadRequest());
        list(2, 101, null).andExpect(status().isBadRequest());
        list(2, null, "깨진-커서").andExpect(status().isBadRequest());
        // 형식(base64)은 맞지만 날짜가 아닌 값
        list(2, null, cursorOf("abc")).andExpect(status().isBadRequest());
    }

    // 연도가 DB 가 받는 범위(1~9999)를 벗어난 날짜를 그대로 쿼리에 넣으면 DB 오류(500)가 된다
    @Test
    void 연도가_범위를_벗어난_커서는_500이_아니라_400이다() throws Exception {
        list(2, null, cursorOf("+99999-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("-0001-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("0000-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("9999-12-31")).andExpect(status().isOk()); // 범위의 끝은 허용
    }

    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
