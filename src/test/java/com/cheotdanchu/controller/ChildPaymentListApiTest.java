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
import java.util.Map;

import static com.cheotdanchu.controller.CardPaymentFixture.insert;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산(2026-09-26~10-25)에 카드 결제 14건. 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4)는 진행 중인 정산이 없다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildPaymentListApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions list(int personaId, String sort, String from, String to, Integer size, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/child/payments").header("X-Persona-Id", personaId);
        if (sort != null) request.param("sort", sort);
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);
        if (size != null) request.param("size", size.toString());
        if (cursor != null) request.param("cursor", cursor);
        return mockMvc.perform(request);
    }

    private ResultActions list(String sort, Integer size, String cursor) throws Exception {
        return list(2, sort, null, null, size, cursor);
    }

    /** 정렬 기준에 맞는 결제 ID 순서를 DB 에서 직접 구한다 */
    private List<Long> expectedIds(String orderBy) {
        return jdbc.queryForList("SELECT payment_id FROM card_payments WHERE member_id = 2 ORDER BY " + orderBy, Long.class);
    }

    /** 첫 페이지부터 nextCursor 를 따라 끝까지 읽은 결제 ID 순서 */
    private List<Long> readAllPages(String sort, int size) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            String body = list(sort, size, cursor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.data.items[*].paymentId");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.data.nextCursor");
            if (cursor == null) return ids;
        }
        throw new AssertionError("페이지가 끝나지 않아요");
    }

    @Test
    void 기본은_날짜순으로_결제의_가맹점명_카테고리_금액을_본다() throws Exception {
        Map<String, Object> newest = jdbc.queryForMap(
                "SELECT payment_id, merchant_name, category, amount FROM card_payments WHERE member_id = 2 ORDER BY paid_at DESC, payment_id DESC LIMIT 1");

        list(null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.totalCount").value(14))
                .andExpect(jsonPath("$.data.items.length()").value(14))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].paymentId").value(((Number) newest.get("payment_id")).longValue()))
                .andExpect(jsonPath("$.data.items[0].merchantName").value(newest.get("merchant_name")))
                .andExpect(jsonPath("$.data.items[0].category").value(newest.get("category")))
                .andExpect(jsonPath("$.data.items[0].amount").value(newest.get("amount")))
                .andExpect(jsonPath("$.data.items[0].isCanceled").value(false))
                .andExpect(jsonPath("$.data.items[0].isHighAmount").value(false));
    }

    @Test
    void 날짜순과_큰_금액순_순서가_DB와_같다() throws Exception {
        assertEquals(expectedIds("paid_at DESC, payment_id DESC"), readAllPages(null, 20));
        assertEquals(expectedIds("paid_at DESC, payment_id DESC"), readAllPages("date", 20));
        assertEquals(expectedIds("amount DESC, payment_id DESC"), readAllPages("amount", 20));
    }

    @Test
    void 커서로_페이지를_넘기면_빠지거나_겹치는_결제가_없다() throws Exception {
        // 같은 시각에 결제가 여러 건 있어도(순서를 ID 로 정한다) 페이지 경계에서 빠지지 않아야 한다
        for (int i = 0; i < 3; i++) insert(jdbc, "같은 시각 " + i, "식사", 5000, false, null, "2026-10-19 12:00:00");

        for (int size : new int[]{1, 2, 5, 7}) {
            assertEquals(expectedIds("paid_at DESC, payment_id DESC"), readAllPages("date", size), "날짜순 size=" + size);
            assertEquals(expectedIds("amount DESC, payment_id DESC"), readAllPages("amount", size), "금액순 size=" + size);
        }
    }

    @Test
    void 한_페이지가_가득_차도_다음이_없으면_nextCursor는_null이다() throws Exception {
        list("date", 14, null)
                .andExpect(jsonPath("$.data.items.length()").value(14))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
        list("date", 13, null)
                .andExpect(jsonPath("$.data.items.length()").value(13))
                .andExpect(jsonPath("$.data.totalCount").value(14));
    }

    @Test
    void 고른_기간의_결제만_센다() throws Exception {
        String day = jdbc.queryForObject("SELECT DATE_FORMAT(MIN(paid_at), '%Y-%m-%d') FROM card_payments WHERE member_id = 2", String.class);
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM card_payments WHERE member_id = 2 AND DATE(paid_at) = ?", Integer.class, day);

        list(2, null, day, day, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(count))
                .andExpect(jsonPath("$.data.items.length()").value(count));
    }

    @Test
    void 취소는_마이너스_금액이고_큰_금액순에서는_맨_뒤이며_위험_결제는_표시된다() throws Exception {
        insert(jdbc, "문구나라", "쇼핑", -3800, true, null, "2026-10-18 16:10:00");
        insert(jdbc, "명품관", "쇼핑", 300000, false, "HIGH_AMOUNT", "2026-10-18 17:00:00");

        list("amount", null, null)
                .andExpect(jsonPath("$.data.items[0].merchantName").value("명품관"))
                .andExpect(jsonPath("$.data.items[0].isHighAmount").value(true))
                .andExpect(jsonPath("$.data.items[0].isCanceled").value(false))
                .andExpect(jsonPath("$.data.items[15].merchantName").value("문구나라"))
                .andExpect(jsonPath("$.data.items[15].amount").value(-3800))
                .andExpect(jsonPath("$.data.items[15].isCanceled").value(true))
                .andExpect(jsonPath("$.data.items[15].isHighAmount").value(false));
    }

    @Test
    void 요약의_청구액과_목록_금액의_합이_같다() throws Exception {
        insert(jdbc, "문구나라", "쇼핑", -3800, true, null, "2026-10-18 16:10:00");
        String body = list(null, null, null).andReturn().getResponse().getContentAsString();
        List<Number> amounts = JsonPath.read(body, "$.data.items[*].amount");

        long sum = amounts.stream().mapToLong(Number::longValue).sum();
        mockMvc.perform(get("/child/payments/summary").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.billedAmount").value(sum));
    }

    @Test
    void 잘못된_정렬_크기_커서_기간은_400() throws Exception {
        list("foo", null, null).andExpect(status().isBadRequest());
        list(null, 0, null).andExpect(status().isBadRequest());
        list(null, 101, null).andExpect(status().isBadRequest());
        list(null, null, "깨진-커서").andExpect(status().isBadRequest());
        list(2, null, "2026-09-25", null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 기간(2026-09-26~2026-10-25) 안에서 골라주세요"));
        list(2, null, "2026-10-20", "2026-10-10", null, null).andExpect(status().isBadRequest());
    }

    /** 형식(base64)은 맞지만 안의 값이 숫자·시각이 아닌 커서를 만든다 */
    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 형식은_맞지만_안의_값이_깨진_커서는_400이다() throws Exception {
        list("date", null, cursorOf("d|abc|1")).andExpect(status().isBadRequest());               // 시각이 아님
        list("date", null, cursorOf("d|2026-10-19T12:00:00|abc")).andExpect(status().isBadRequest()); // ID 가 숫자가 아님
        list("amount", null, cursorOf("a|abc|1")).andExpect(status().isBadRequest());             // 금액이 숫자가 아님
        list("amount", null, cursorOf("a|1000")).andExpect(status().isBadRequest());              // 조각이 모자람
    }

    @Test
    void from이나_to_하나만_줘도_그_범위만_센다() throws Exception {
        String middle = jdbc.queryForObject(
                "SELECT DATE_FORMAT(DATE_ADD(MIN(paid_at), INTERVAL 5 DAY), '%Y-%m-%d') FROM card_payments WHERE member_id = 2", String.class);
        int fromCount = jdbc.queryForObject("SELECT COUNT(*) FROM card_payments WHERE member_id = 2 AND DATE(paid_at) >= ?", Integer.class, middle);
        int toCount = jdbc.queryForObject("SELECT COUNT(*) FROM card_payments WHERE member_id = 2 AND DATE(paid_at) <= ?", Integer.class, middle);

        list(2, null, middle, null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(fromCount));
        list(2, null, null, middle, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(toCount));
    }

    @Test
    void 다른_정렬에서_받은_커서는_쓸_수_없다() throws Exception {
        String dateCursor = JsonPath.read(list("date", 5, null).andReturn().getResponse().getContentAsString(), "$.data.nextCursor");
        assertNotNull(dateCursor);

        list("amount", 5, dateCursor).andExpect(status().isBadRequest());
    }

    @Test
    void 진행_중인_정산이_없으면_빈_목록이다() throws Exception {
        list(4, null, null, null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 부모가_조회하면_입력값이_잘못돼도_403이다() throws Exception {
        list(1, null, null, null, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
        list(1, "foo", null, null, 0, null).andExpect(status().isForbidden());
    }
}
