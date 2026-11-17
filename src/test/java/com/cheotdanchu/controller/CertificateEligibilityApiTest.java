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

import static com.cheotdanchu.controller.SettlementFixture.completeCurrentSettlement;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 A 루피(id 2, 11/2에 만 19세)는 검증된 정산 11회, 이번 정산(결제일 10-25)이 12번째.
 * 가족 B 뽀로로(id 4)는 이미 만 19세이고 검증된 정산이 12회를 넘는다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateEligibilityApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions eligibility(int personaId) throws Exception {
        return mockMvc.perform(get("/child/certificates/eligibility").header("X-Persona-Id", personaId));
    }

    @Test
    void 가족A는_생일도_정산도_모자라서_더_늦은_생일이_가능일이다() throws Exception {
        eligibility(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.ageReached").value(false))
                .andExpect(jsonPath("$.data.birthdayDDay").value(14))
                .andExpect(jsonPath("$.data.settlementCount").value(11))
                .andExpect(jsonPath("$.data.settlementRequired").value(12))
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-11-02"));
    }

    @Test
    void 이미_만_19세이고_정산_12회를_채운_가족B는_바로_발급할_수_있다() throws Exception {
        eligibility(4)
                .andExpect(jsonPath("$.data.ageReached").value(true))
                .andExpect(jsonPath("$.data.birthdayDDay").value(0))
                .andExpect(jsonPath("$.data.settlementCount").value(12)) // 12회를 넘어도 요건 횟수로 맞춰 보여준다
                .andExpect(jsonPath("$.data.canIssue").value(true))
                .andExpect(jsonPath("$.data.availableFrom").value(nullValue()));
    }

    @Test
    void 만_19세여도_정산이_모자라면_발급할_수_없고_12회째_결제일이_가능일이다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");

        eligibility(2)
                .andExpect(jsonPath("$.data.ageReached").value(true))
                .andExpect(jsonPath("$.data.birthdayDDay").value(0))
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-10-25"));
    }

    @Test
    void 정산_12회를_채웠어도_만_19세가_안_됐으면_생일이_가능일이다() throws Exception {
        completeCurrentSettlement(jdbc);

        eligibility(2)
                .andExpect(jsonPath("$.data.ageReached").value(false))
                .andExpect(jsonPath("$.data.settlementCount").value(12))
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-11-02"));
    }

    @Test
    void 두_요건을_모두_채우면_발급할_수_있다() throws Exception {
        completeCurrentSettlement(jdbc);
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2"); // 오늘이 만 19세 생일

        eligibility(2)
                .andExpect(jsonPath("$.data.ageReached").value(true))
                .andExpect(jsonPath("$.data.canIssue").value(true))
                .andExpect(jsonPath("$.data.availableFrom").value(nullValue()));
    }

    @Test
    void 제외된_정산은_횟수에서_뺀다() throws Exception {
        completeCurrentSettlement(jdbc);
        jdbc.update("UPDATE settlements SET is_excluded = TRUE WHERE family_id = 1 AND status = 'COMPLETED' "
                + "AND period_end < '2026-10-25' ORDER BY period_end DESC LIMIT 1"); // 부모 지원 등으로 제외
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");

        eligibility(2)
                .andExpect(jsonPath("$.data.settlementCount").value(11))
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-11-25")); // 다음 정산 결제일
    }

    @Test
    void 정산이_지연돼_추정일이_지나도_가능일은_오늘보다_앞서지_않는다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");
        jdbc.update("UPDATE settlements SET due_at = '2026-10-18 18:00:00' WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end");

        eligibility(2)
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-10-19"));
    }

    @Test
    void 진행_중인_정산이_없고_정산이_모자라면_가능일은_오늘이다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");
        jdbc.update("UPDATE settlements SET period_end = '2026-10-18' WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end");

        eligibility(2)
                .andExpect(jsonPath("$.data.canIssue").value(false))
                .andExpect(jsonPath("$.data.availableFrom").value("2026-10-19"));
    }

    @Test
    void 자녀_홈의_확인서_D_day와_같은_날짜를_쓴다() throws Exception {
        String home = mockMvc.perform(get("/child/home").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();

        eligibility(2).andExpect(jsonPath("$.data.availableFrom").value("2026-11-02")); // 10-19 + 14일
        assertEquals(14, ((Number) JsonPath.read(home, "$.data.chips.certificateDDay")).intValue());
    }

    @Test
    void 부모가_조회하면_403() throws Exception {
        eligibility(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
