package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.cheotdanchu.controller.CardPaymentFixture.insert;
import static com.cheotdanchu.controller.CardPaymentFixture.insertCanceled;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 A(루피 엄마 id 1, 루피 id 2, 11/2 생일)의 이번 정산(09-26~10-25)은 14건 79,500원이고 검증된 정산이 11회.
 * 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4)는 진행 중인 정산이 없다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildHomeApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions home(int personaId) throws Exception {
        return mockMvc.perform(get("/child/home").header("X-Persona-Id", personaId));
    }

    private ResultActions daily(int personaId) throws Exception {
        return mockMvc.perform(get("/child/home/daily-spending").header("X-Persona-Id", personaId));
    }

    @Test
    void 가족A_자녀_홈은_칩과_정산과_점검_카드를_한번에_준다() throws Exception {
        home(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.today").value("2026-10-19"))
                .andExpect(jsonPath("$.data.member.memberId").value(2))
                .andExpect(jsonPath("$.data.member.name").value("루피"))
                .andExpect(jsonPath("$.data.chips.streakMonths").value(6))
                .andExpect(jsonPath("$.data.chips.certificateDDay").value(14))
                .andExpect(jsonPath("$.data.chips.visibility").value("TOTAL_ONLY"))
                .andExpect(jsonPath("$.data.settlement.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.settlement.dDay").value(6))
                .andExpect(jsonPath("$.data.settlement.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.settlement.paymentCount").value(14))
                .andExpect(jsonPath("$.data.settlement.availableAmount").value(73000))
                .andExpect(jsonPath("$.data.settlement.carryoverAmount").value(59900))
                .andExpect(jsonPath("$.data.settlement.shortfallAmount").value(6500))
                .andExpect(jsonPath("$.data.settlement.cardLinked").value(true))
                .andExpect(jsonPath("$.data.shortfallCheckShow").value(true));
    }

    @Test
    void 홈의_정산은_현재_정산_조회와_같고_점검_카드는_부족_예상_조회와_같다() throws Exception {
        String current = mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();
        String check = mockMvc.perform(get("/settlements/current/check").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();

        String home = home(2).andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.<Object>read(current, "$.data"), JsonPath.<Object>read(home, "$.data.settlement"));
        assertEquals(JsonPath.<Object>read(check, "$.data.visible"), JsonPath.<Object>read(home, "$.data.shortfallCheckShow"));
    }

    @Test
    void 부족하지_않으면_점검_카드를_보이지_않는다() throws Exception {
        jdbc.update("UPDATE accounts SET balance = balance + 10000 WHERE member_id = 2"); // 쓸 수 있는 돈이 청구액을 넘는다

        home(2)
                .andExpect(jsonPath("$.data.shortfallCheckShow").value(false))
                .andExpect(jsonPath("$.data.settlement.surplusAmount").value(3500));
    }

    @Test
    void 성년_전환한_가족B는_정산이_null이고_확인서를_바로_발급할_수_있다() throws Exception {
        home(4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.name").value("뽀로로"))
                .andExpect(jsonPath("$.data.settlement").value(nullValue()))
                .andExpect(jsonPath("$.data.shortfallCheckShow").value(false))
                .andExpect(jsonPath("$.data.chips.certificateDDay").value(nullValue()))
                .andExpect(jsonPath("$.data.chips.visibility").value("TOTAL_ONLY"));
    }

    @Test
    void 생일보다_정산_12회가_늦게_채워지면_정산_기준으로_센다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2"); // 생일 요건은 이미 채움

        // 검증된 정산이 11회라 이번 정산(결제일 10-25)이 12번째
        home(2).andExpect(jsonPath("$.data.chips.certificateDDay").value(6));
    }

    /** 이번 정산까지 끝나서 검증된 정산이 12회가 된 상태로 만든다 */
    private void completeCurrentSettlement() {
        jdbc.update("UPDATE settlements SET status = 'COMPLETED', result = 'FULL_PAID', billed_amount = 79500, balance_at_due = 90000 "
                + "WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end");
    }

    @Test
    void 정산_12회를_채웠고_오늘이_생일이면_D0을_준다() throws Exception {
        completeCurrentSettlement();
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2");

        home(2).andExpect(jsonPath("$.data.chips.certificateDDay").value(0));
    }

    @Test
    void 정산_12회를_채웠고_생일이_지났으면_null이다() throws Exception {
        completeCurrentSettlement();
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");

        home(2).andExpect(jsonPath("$.data.chips.certificateDDay").value(nullValue()));
    }

    @Test
    void 이번_정산이_이미_끝났어도_다음_정산부터_센다() throws Exception {
        completeCurrentSettlement();
        jdbc.update("UPDATE settlements SET is_excluded = TRUE WHERE family_id = 1 AND status = 'COMPLETED' "
                + "AND period_end < '2026-10-25' ORDER BY period_end DESC LIMIT 1"); // 제외돼서 검증된 정산이 11회
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");

        home(2).andExpect(jsonPath("$.data.chips.certificateDDay").value(37)); // 다음 정산 결제일 11-25
    }

    @Test
    void 부족이_났던_달이_끼면_연속_완납이_끊긴다() throws Exception {
        jdbc.update("UPDATE settlements SET result = 'SELF_RESOLVED' WHERE family_id = 1 AND status = 'COMPLETED' "
                + "ORDER BY period_end DESC LIMIT 1");

        home(2).andExpect(jsonPath("$.data.chips.streakMonths").value(0));
    }

    @Test
    void 부모가_호출하면_둘_다_403() throws Exception {
        home(1).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
        daily(1).andExpect(status().isForbidden());
    }

    @Test
    void 일별_지출은_정산_시작일부터_오늘까지만_하루씩_준다() throws Exception {
        String body = daily(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodStart").value("2026-09-26"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.data.today").value("2026-10-19"))
                .andExpect(jsonPath("$.data.allowanceAmount").value(70000))
                .andExpect(jsonPath("$.data.items.length()").value(24)) // 9/26 ~ 10/19
                .andExpect(jsonPath("$.data.items[0].date").value("2026-09-26"))
                .andExpect(jsonPath("$.data.items[23].date").value("2026-10-19"))
                .andReturn().getResponse().getContentAsString();

        // 하루씩의 합이 청구 예정액과 같다
        List<Integer> amounts = JsonPath.read(body, "$.data.items[*].amount");
        assertEquals(79500, amounts.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void 결제가_없는_날은_0이고_취소는_그날에_합산한다() throws Exception {
        String before = daily(2).andReturn().getResponse().getContentAsString();
        insert(jdbc, "편의점", "식사", 12000, false, null, "2026-10-19 12:00:00");
        insertCanceled(jdbc, "식사", -2000, "2026-10-19 15:00:00");

        String after = daily(2).andReturn().getResponse().getContentAsString();

        assertEquals(amountOf(before, "2026-10-19") + 10000, amountOf(after, "2026-10-19"));
        assertEquals(amountOf(before, "2026-10-18"), amountOf(after, "2026-10-18"));
    }

    @Test
    void 결제가_하나도_없는_날은_0이다() throws Exception {
        jdbc.update("DELETE FROM card_payments WHERE member_id = 2 AND DATE(paid_at) = '2026-10-19'");

        daily(2).andExpect(jsonPath("$.data.items[23].amount").value(0));
    }

    @Test
    void 오늘_이후에_찍힌_결제는_내려주지_않는다() throws Exception {
        insert(jdbc, "편의점", "식사", 5000, false, null, "2026-10-20 12:00:00");

        daily(2).andExpect(jsonPath("$.data.items.length()").value(24))
                .andExpect(jsonPath("$.data.items[23].date").value("2026-10-19"));
    }

    @Test
    void 진행_중인_정산이_없으면_일별_지출은_data가_null인_200() throws Exception {
        daily(4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private static int amountOf(String body, String date) {
        List<Integer> amounts = JsonPath.read(body, "$.data.items[?(@.date == '" + date + "')].amount");
        return amounts.get(0);
    }
}
