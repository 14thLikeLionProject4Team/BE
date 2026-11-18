package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.cheotdanchu.controller.SettlementFixture.completeCurrentSettlement;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 B 뽀로로(id 4)는 이미 만 19세이고 검증된 정산이 12회를 넘어 바로 발급할 수 있다.
 * 가족 A 루피(id 2)는 11/2에 만 19세가 되고 정산이 11회라 아직 못 한다. DB 는 테스트마다 롤백되고, PDF 는 build/test-certificates 에 남는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateIssueApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.certificate-dir}")
    String certificateDir;

    private ResultActions issue(int personaId, String body) throws Exception {
        return mockMvc.perform(post("/child/certificates").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions issue(int personaId) throws Exception {
        return issue(personaId, "{\"identityVerified\":true}");
    }

    private int certificateCount(int memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM certificates WHERE member_id = ?", Integer.class, memberId);
    }

    @Test
    void 요건을_채운_자녀는_발급하고_90일_유효하다() throws Exception {
        issue(4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("확인서를 발급했어요"))
                .andExpect(jsonPath("$.data.certificateId").isNumber())
                .andExpect(jsonPath("$.data.issuedAt").value(startsWith("2026-10-19T"))) // 시연 기준일
                .andExpect(jsonPath("$.data.expiresAt").value(startsWith("2027-01-17T"))); // 90일 뒤
    }

    @Test
    void 발급한_때의_지표가_그대로_저장된다() throws Exception {
        issue(4);

        // 뽀로로: 27개월 중 24개월 완납, 최장 연속 15개월, 부족 3번 모두 스스로 해소
        Object[] row = jdbc.queryForObject("SELECT full_paid_months, total_months, max_streak, self_resolved_count, shortfall_count "
                + "FROM certificates WHERE member_id = 4",
                (rs, i) -> new Object[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)});
        assertEquals(24, row[0]);
        assertEquals(27, row[1]);
        assertEquals(15, row[2]);
        assertEquals(3, row[3]);
        assertEquals(3, row[4]);
    }

    @Test
    void 발급하면_PDF_파일이_저장되고_내용은_양식_테스트가_확인한다() throws Exception {
        String body = issue(4).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.certificateId")).longValue();

        String fileName = jdbc.queryForObject("SELECT file_path FROM certificates WHERE certificate_id = ?", String.class, id);
        Path file = Path.of(certificateDir).toAbsolutePath().resolve(fileName);
        assertTrue(Files.exists(file), "PDF 파일이 저장돼야 해요: " + file);
    }

    /** 글꼴에 따라 띄어쓰기·괄호가 다른 문자로 뽑히기도 해서, 한글·숫자·영문·/ % . - 만 남기고 붙여서 비교한다 */
    private static String norm(String s) {
        return s.replaceAll("[^가-힣0-9A-Za-z/%.\\-]", "");
    }

    private static void assertContains(String text, String... expected) {
        for (String e : expected) assertTrue(text.contains(norm(e)), "PDF 에 없어요: " + e + "\n" + text);
    }

    /** 발급한 확인서 PDF 의 글을 norm 으로 정리해서 뽑는다 */
    private String pdfText(long certificateId, int expectedPages) throws Exception {
        String fileName = jdbc.queryForObject("SELECT file_path FROM certificates WHERE certificate_id = ?", String.class, certificateId);
        try (PDDocument pdf = Loader.loadPDF(Path.of(certificateDir).toAbsolutePath().resolve(fileName).toFile())) {
            assertEquals(expectedPages, pdf.getNumberOfPages());
            return norm(new PDFTextStripper().getText(pdf));
        }
    }

    @Test
    void 팀_양식대로_한_쪽에_네_부분이_들어간다() throws Exception {
        long id = ((Number) JsonPath.read(issue(4).andReturn().getResponse().getContentAsString(), "$.data.certificateId")).longValue();

        String text = pdfText(id, 1);

        // 위: 발급번호·제목·합성 데이터 안내
        assertContains(text, String.format("발급번호 CD-2026-1019-%04d", id), "금융행동 기록 확인서", "합성 데이터 시연 · 실제 발급 문서가 아닙니다");
        // 1. 발급 정보
        assertContains(text, "1. 발급 정보", "성명 뽀로로", "출생연도 2007년", "유효기간 2027. 01. 17.까지 (발급일부터 90일)", "본인 동의 본인이 직접 발급했으며", "진위 확인");
        // 2. 기록 개요
        assertContains(text, "관찰 기간 2024. 07. ~ 2026. 09. (27개월)", "100% (검증된 정산 27회 / 전체 정산 27회)", "월 평균 5만~10만 원 구간", "검증된 정산 12회 이상");
        // 3. 결제일 정산 기록 (전체 기간과 최근 12개월)
        assertContains(text, "전체 기간 (27개월) 최근 12개월", "89% (24/27개월) 100% (12/12개월)", "15개월 12개월", "100% (3/3건) 해당 없음(부족 0건)", "4.3일 해당 없음");
        // 4. 읽는 분께
        assertContains(text, "4. 읽는 분께", "신용평점이나 신용등급이 아니며");
    }

    @Test
    void 저축_이행률은_양식에서_뺐다() throws Exception {
        long id = ((Number) JsonPath.read(issue(4).andReturn().getResponse().getContentAsString(), "$.data.certificateId")).longValue();

        assertFalse(pdfText(id, 1).contains("저축"));
    }

    @Test
    void PDF의_전체_기간_지표는_미리보기와_같은_숫자다() throws Exception {
        long id = ((Number) JsonPath.read(issue(4).andReturn().getResponse().getContentAsString(), "$.data.certificateId")).longValue();
        String preview = mockMvc.perform(get("/child/certificates/preview").header("X-Persona-Id", 4)).andReturn().getResponse().getContentAsString();
        int fullPaid = JsonPath.read(preview, "$.data.fullPaidMonths");
        int total = JsonPath.read(preview, "$.data.totalMonths");
        int streak = JsonPath.read(preview, "$.data.maxStreak");
        double margin = JsonPath.read(preview, "$.data.avgMarginRate");

        String text = pdfText(id, 1);

        assertTrue(text.contains(fullPaid + "/" + total + "개월"), text);
        assertTrue(text.contains(streak + "개월"), text);
        assertTrue(text.contains("평균" + margin + "%"), text); // 여유율은 명세 정의 그대로
    }

    @Test
    void 발급번호는_연도_월일_확인서_번호로_만든다() throws Exception {
        long id = ((Number) JsonPath.read(issue(4).andReturn().getResponse().getContentAsString(), "$.data.certificateId")).longValue();

        assertTrue(pdfText(id, 1).contains(String.format("CD-2026-1019-%04d", id)));
    }

    @Test
    void 발급한_확인서는_최신_조회에서_보인다() throws Exception {
        String body = issue(4).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.certificateId")).longValue();

        mockMvc.perform(get("/child/certificates/latest").header("X-Persona-Id", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateId").value(id))
                .andExpect(jsonPath("$.data.fullPaidMonths").value(24))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    void 이미_유효한_확인서가_있으면_다시_발급하지_않는다() throws Exception {
        issue(4).andExpect(status().isCreated());

        issue(4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 유효한 확인서가 있어요"));
        assertEquals(1, certificateCount(4));
    }

    @Test
    void 이전_확인서가_만료됐으면_새로_발급할_수_있고_그게_최신이_된다() throws Exception {
        jdbc.update("INSERT INTO certificates (member_id, full_paid_months, total_months, max_streak, avg_margin_rate, "
                + "self_resolved_count, shortfall_count, file_path, issued_at, expires_at) "
                + "VALUES (4, 20, 23, 10, 5.0, 1, 2, 'old.pdf', '2026-05-01 10:00:00', '2026-07-30 10:00:00')");

        long id = ((Number) JsonPath.read(issue(4).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.data.certificateId")).longValue();

        mockMvc.perform(get("/child/certificates/latest").header("X-Persona-Id", 4))
                .andExpect(jsonPath("$.data.certificateId").value(id))
                .andExpect(jsonPath("$.data.fullPaidMonths").value(24));
    }

    @Test
    void 정산이_지연돼_가능일이_오늘이면_날짜_없이_아직이라고_알린다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2000-01-01' WHERE member_id = 2");
        jdbc.update("UPDATE settlements SET due_at = '2026-10-18 18:00:00' WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end");

        issue(2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아직 발급 요건을 채우지 못했어요"));
    }

    @Test
    void 요건을_못_채웠으면_가능일을_알려주고_409() throws Exception {
        issue(2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("11월 2일부터 발급할 수 있어요"));

        assertEquals(0, certificateCount(2));
    }

    @Test
    void 두_요건을_모두_채우면_루피도_발급한다() throws Exception {
        completeCurrentSettlement(jdbc);
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2"); // 오늘 만 19세

        issue(2).andExpect(status().isCreated());
        assertEquals(1, certificateCount(2));
    }

    @Test
    void 본인_인증을_안_했으면_400이고_발급하지_않는다() throws Exception {
        issue(4, "{\"identityVerified\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인 인증을 먼저 해주세요"));

        assertEquals(0, certificateCount(4));
    }

    @Test
    void 본인_인증_값이_없으면_400() throws Exception {
        issue(4, "{}").andExpect(status().isBadRequest());
        assertEquals(0, certificateCount(4));
    }

    @Test
    void 부모가_발급하면_403() throws Exception {
        issue(3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
