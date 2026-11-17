package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;


import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이(부모)의 약속 요약과 성년 전환 카드. 시연 기준일 2026-10-19.
 * 가족 A: 루피 엄마(id 1), 루피(id 2, 11/2에 만 19세, 끝난 정산 11회). 가족 B: 뽀로로 엄마(id 3), 뽀로로(id 4, 2026-10-03에 만 19세, 끝난 정산 27회).
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParentCardsApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions promise(int personaId, String query) throws Exception {
        return mockMvc.perform(get("/parent/promise" + query).header("X-Persona-Id", personaId));
    }

    private ResultActions adultTransition(int personaId, String query) throws Exception {
        return mockMvc.perform(get("/parent/adult-transition" + query).header("X-Persona-Id", personaId));
    }

    // ── 약속 요약 ──

    @Test
    void 루피_엄마의_약속_요약은_명세_예시와_같다() throws Exception {
        promise(1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.streakMonths").value(6))
                .andExpect(jsonPath("$.data.allowance.current").value(70000))
                .andExpect(jsonPath("$.data.allowanceDay").value(1))
                .andExpect(jsonPath("$.data.autoTransfer").value(true))
                .andExpect(jsonPath("$.data.settlementDay").value(25))
                .andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"))
                .andExpect(jsonPath("$.data.missionCapRate").value(30))
                .andExpect(jsonPath("$.data.missionCapAmount").value(21000))
                .andExpect(jsonPath("$.data.cardLimit").value(100000));
    }

    @Test
    void 바뀔_예정이_없으면_예정_용돈은_null이다() throws Exception {
        promise(1, "")
                .andExpect(jsonPath("$.data.allowance.next").value(nullValue()))
                .andExpect(jsonPath("$.data.allowance.nextEffectiveFrom").value(nullValue()));
    }

    @Test
    void 용돈을_바꿔_저장하면_예정_용돈과_적용일이_나온다() throws Exception {
        mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":73000,\"reason\":\"INFLATION\",\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25}"))
                .andExpect(status().isCreated());

        promise(1, "")
                .andExpect(jsonPath("$.data.allowance.current").value(70000))
                .andExpect(jsonPath("$.data.allowance.next").value(73000))
                .andExpect(jsonPath("$.data.allowance.nextEffectiveFrom").value("2026-11-01"));
    }

    @Test
    void 미션_상한을_바꾸면_약속_요약의_상한_금액도_같이_바뀐다() throws Exception {
        mockMvc.perform(patch("/family-settings/mission-cap").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capRate\":50}"))
                .andExpect(status().isOk());

        promise(1, "")
                .andExpect(jsonPath("$.data.missionCapRate").value(50))
                .andExpect(jsonPath("$.data.missionCapAmount").value(35000));
    }

    @Test
    void 미션_상한_금액은_월_보상_합계와_같은_값이다() throws Exception {
        String summary = mockMvc.perform(get("/missions/rewards/summary").header("X-Persona-Id", 1)).andReturn().getResponse().getContentAsString();
        long cap = ((Number) JsonPath.read(summary, "$.data.capAmount")).longValue();

        promise(1, "").andExpect(jsonPath("$.data.missionCapAmount").value(cap));
    }

    @Test
    void 카드_한도가_정해지지_않았으면_null이다() throws Exception {
        jdbc.update("UPDATE family_settings SET card_limit = NULL WHERE family_id = 1");

        promise(1, "").andExpect(jsonPath("$.data.cardLimit").value(nullValue()));
    }

    @Test
    void 연속_완납이_끊긴_가족은_배지가_0이다() throws Exception {
        jdbc.update("UPDATE settlements SET result = 'SELF_RESOLVED' WHERE family_id = 1 AND status = 'COMPLETED' "
                + "ORDER BY period_end DESC LIMIT 1");

        promise(1, "").andExpect(jsonPath("$.data.streakMonths").value(0));
    }

    @Test
    void childId를_주면_우리_가족의_아이인지_확인한다() throws Exception {
        promise(1, "?childId=2").andExpect(status().isOk());
        promise(1, "?childId=4").andExpect(status().isNotFound()); // 다른 가족의 아이
        promise(1, "?childId=1").andExpect(status().isNotFound()); // 아이가 아니라 부모
        promise(1, "?childId=999").andExpect(status().isNotFound());
    }

    @Test
    void 아이가_성년이_된_가족의_부모는_약속_요약을_못_본다() throws Exception {
        promise(3, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기록이 본인에게 넘어갔어요."));
    }

    @Test
    void 만_19세_생일_하루_전까지는_약속_요약을_본다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-20' WHERE member_id = 2"); // 내일이 생일

        promise(1, "").andExpect(status().isOk());
    }

    @Test
    void 만_19세_생일_당일부터_약속_요약이_막힌다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2"); // 오늘이 생일

        promise(1, "").andExpect(status().isForbidden());
    }

    @Test
    void 자녀가_부모_약속_요약을_조회하면_403() throws Exception {
        promise(2, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    // ── 성년 전환 카드 ──

    @Test
    void 아이가_아직_만_19세_전이면_전환_전_카드다() throws Exception {
        adultTransition(1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.transitioned").value(false))
                .andExpect(jsonPath("$.data.at").value(nullValue()))
                .andExpect(jsonPath("$.data.togetherCount").value(nullValue()))
                .andExpect(jsonPath("$.data.recordsHidden").value(false));
    }

    @Test
    void 이미_만_19세가_된_뽀로로_엄마는_전환_카드와_함께한_결제일_횟수를_본다() throws Exception {
        adultTransition(3, "")
                .andExpect(jsonPath("$.data.transitioned").value(true))
                .andExpect(jsonPath("$.data.at").value("2026-10-03"))
                .andExpect(jsonPath("$.data.togetherCount").value(27))
                .andExpect(jsonPath("$.data.recordsHidden").value(true));
    }

    @Test
    void 전환_카드에는_숫자_기록이_없다() throws Exception {
        adultTransition(3, "")
                .andExpect(jsonPath("$.data.length()").value(4)) // 전환 여부·전환일·함께한 횟수·기록 숨김, 그 밖의 숫자 기록은 없다
                .andExpect(jsonPath("$.data.transitioned").exists())
                .andExpect(jsonPath("$.data.at").exists())
                .andExpect(jsonPath("$.data.togetherCount").exists())
                .andExpect(jsonPath("$.data.recordsHidden").exists());
    }

    @Test
    void 만_19세_생일_당일부터_전환이고_하루_전까지는_전환_전이다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-20' WHERE member_id = 2"); // 내일이 생일
        adultTransition(1, "").andExpect(jsonPath("$.data.transitioned").value(false));
    }

    @Test
    void 생일_당일에는_그날까지_끝난_정산만_함께한_횟수로_센다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2"); // 오늘이 생일

        adultTransition(1, "")
                .andExpect(jsonPath("$.data.transitioned").value(true))
                .andExpect(jsonPath("$.data.at").value("2026-10-19"))
                .andExpect(jsonPath("$.data.togetherCount").value(11)); // 끝난 11회, 진행 중인 이번 정산(10-25)은 아직
    }

    @Test
    void 전환_뒤에_끝난_정산은_함께한_횟수에_세지_않는다() throws Exception {
        // 뽀로로는 2026-10-03 에 전환. 그 뒤(종료일 10-25)에 끝난 정산이 있어도 함께한 횟수는 그대로 27
        jdbc.update("INSERT INTO settlements (family_id, period_start, period_end, due_at, billed_amount, balance_at_due, paid_amount, status, result) "
                + "VALUES (2, '2026-09-26', '2026-10-25', '2026-10-25 18:00:00', 50000, 60000, 50000, 'COMPLETED', 'FULL_PAID')");

        adultTransition(3, "").andExpect(jsonPath("$.data.togetherCount").value(27));
    }

    @Test
    void 전환_카드도_childId를_확인한다() throws Exception {
        adultTransition(3, "?childId=4").andExpect(status().isOk());
        adultTransition(3, "?childId=2").andExpect(status().isNotFound()); // 다른 가족의 아이
    }

    @Test
    void 자녀가_부모_전환_카드를_조회하면_403() throws Exception {
        adultTransition(4, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }
}
