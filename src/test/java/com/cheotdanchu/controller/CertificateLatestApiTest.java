package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시연 기준일 2026-10-19. 시드에는 발급한 확인서가 없어서 테스트가 직접 넣는다. 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateLatestApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions latest(int personaId) throws Exception {
        return mockMvc.perform(get("/child/certificates/latest").header("X-Persona-Id", personaId));
    }

    private void insertCertificate(int memberId, int fullPaid, String issuedAt, String expiresAt) {
        jdbc.update("INSERT INTO certificates (member_id, full_paid_months, total_months, max_streak, avg_margin_rate, "
                + "self_resolved_count, shortfall_count, file_path, issued_at, expires_at) VALUES (?, ?, 12, 12, 7.5, 0, 1, 'certificates/test.pdf', ?, ?)",
                memberId, fullPaid, issuedAt, expiresAt);
    }

    @Test
    void 발급한_적이_없으면_404() throws Exception {
        latest(2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("발급한 확인서가 없어요"));
    }

    @Test
    void 발급한_확인서의_지표와_발급_만료_시각을_본다() throws Exception {
        insertCertificate(2, 12, "2026-10-10 10:00:00", "2027-01-08 10:00:00");

        latest(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.fullPaidMonths").value(12))
                .andExpect(jsonPath("$.data.totalMonths").value(12))
                .andExpect(jsonPath("$.data.maxStreak").value(12))
                .andExpect(jsonPath("$.data.avgMarginRate").value(7.5))
                .andExpect(jsonPath("$.data.selfResolvedCount").value(0))
                .andExpect(jsonPath("$.data.shortfallCount").value(1))
                .andExpect(jsonPath("$.data.issuedAt").value("2026-10-10T10:00:00"))
                .andExpect(jsonPath("$.data.expiresAt").value("2027-01-08T10:00:00"))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    void 여러_번_발급했으면_가장_최근_것을_준다() throws Exception {
        insertCertificate(2, 10, "2026-07-01 10:00:00", "2026-09-29 10:00:00");
        insertCertificate(2, 11, "2026-10-10 10:00:00", "2027-01-08 10:00:00");
        insertCertificate(2, 9, "2026-05-01 10:00:00", "2026-07-30 10:00:00");

        latest(2).andExpect(jsonPath("$.data.fullPaidMonths").value(11));
    }

    @Test
    void 만료된_확인서도_내려주고_expired로_알린다() throws Exception {
        insertCertificate(2, 12, "2026-07-01 10:00:00", "2026-09-29 10:00:00");

        latest(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired").value(true));
    }

    @Test
    void 가장_최근_것이_만료됐으면_이전에_유효했던_것이_있어도_만료로_알린다() throws Exception {
        insertCertificate(2, 9, "2026-10-01 10:00:00", "2026-12-30 10:00:00"); // 유효하지만 더 오래된 것
        insertCertificate(2, 11, "2026-10-15 10:00:00", "2026-10-18 10:00:00"); // 더 최근이고 만료

        latest(2)
                .andExpect(jsonPath("$.data.fullPaidMonths").value(11))
                .andExpect(jsonPath("$.data.expired").value(true));
    }

    @Test
    void 만료일_당일까지는_유효하다() throws Exception {
        insertCertificate(2, 12, "2026-07-21 10:00:00", "2026-10-19 10:00:00"); // 시연 기준일이 만료일

        latest(2).andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    void 만료일_다음_날부터_만료다() throws Exception {
        insertCertificate(2, 12, "2026-07-20 10:00:00", "2026-10-18 23:59:59");

        latest(2).andExpect(jsonPath("$.data.expired").value(true));
    }

    @Test
    void 다른_사람의_확인서는_보이지_않는다() throws Exception {
        insertCertificate(4, 12, "2026-10-10 10:00:00", "2027-01-08 10:00:00"); // 뽀로로

        latest(2).andExpect(status().isNotFound());
        latest(4).andExpect(status().isOk());
    }

    @Test
    void 부모가_조회하면_403() throws Exception {
        latest(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
