package com.cheotdanchu.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 확인서 PDF 를 팀이 정한 양식(한 쪽)으로 만든다: 발급 정보 → 기록 개요 → 결제일 정산 기록(전체·최근 12개월) → 읽는 분께.
 * 한글이 나오도록 나눔고딕(src/main/resources/fonts, SIL OFL)을 PDF 에 넣는다.
 */
@Component
public class CertificatePdfWriter {

    private static final String FONT = "fonts/NanumGothic.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd.", Locale.KOREA);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy. MM.", Locale.KOREA);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA);

    private static final float LEFT = 40;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * LEFT;
    private static final float RIGHT = LEFT + WIDTH;
    private static final float CENTER = LEFT + WIDTH / 2;
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float LABEL_GRAY = 0.93f;
    private static final float BODY = 8.5f;
    private static final float LINE = 11.5f;
    private static final float PAD = 5;

    private static final List<String> NOTES = List.of(
            "이 확인서는 신용평점이나 신용등급이 아니며, 신용정보를 제공하는 서류가 아닙니다. 본인이 직접 발급해 제출하는 참고자료입니다.",
            "기록 기간 동안 가족카드의 법적 채무는 본인회원인 부모에게 있었습니다. 이 기록은 자녀가 가정 안에서 부모 계좌로 정산한 기록입니다.",
            "카드사 승인 내역과 계좌이체로 확인된 정산만 반영했습니다. 결제일 전 7일 안에 부모가 추가로 입금한 달('지원 포함')과 용돈이 늦게 들어온 달은 지표에서 뺐습니다.",
            "현금 거래, 개별 결제 내역, 가맹점명은 포함하지 않습니다.",
            "발급번호 또는 QR 로 진위를 확인할 수 있습니다.");

    /** qrUrl 은 QR 코드에 담을 진위 확인 주소 */
    public byte[] write(CertificateReport r, String qrUrl) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                draw(new PdfCanvas(cs, font, PAGE_HEIGHT), r, qrUrl);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("확인서 PDF 를 만들지 못했어요", e);
        }
    }

    private static void draw(PdfCanvas c, CertificateReport r, String qrUrl) throws IOException {
        CertificateReport.Identity id = r.identity();

        c.right(RIGHT, 36, 8, "발급번호 " + id.number() + "   |   페이지 1/1");
        c.centered(CENTER, 72, 22, "금융행동 기록 확인서", true);
        c.hline(LEFT, RIGHT, 86, 2.2f);
        c.box(LEFT, 96, WIDTH, 22, null, 0.8f);
        c.centered(CENTER, 110.5f, 9, "합성 데이터 시연 · 실제 발급 문서가 아닙니다", true);

        float y = 134;
        y = heading(c, "1. 발급 정보", y);
        y = issueTable(c, r, qrUrl, y) + 14;

        y = heading(c, "2. 기록 개요", y);
        y = overviewTable(c, r, y) + 14;

        y = heading(c, "3. 결제일 정산 기록", y);
        y = recordTable(c, r, y);
        c.text(LEFT, y + 11, 8, "※ 최근 12개월: " + MONTH.format(r.recentFrom()) + " ~ " + MONTH.format(r.observedTo()), false);
        y += 26;

        y = heading(c, "4. 읽는 분께", y);
        notes(c, y);

        footer(c, id.issuedOn());
    }

    // ── 1. 발급 정보 ──

    private static float issueTable(PdfCanvas c, CertificateReport r, String qrUrl, float top) throws IOException {
        CertificateReport.Identity id = r.identity();
        float qrWidth = 100;
        float label = 62;
        float v1 = 128;
        float v2 = WIDTH - qrWidth - 2 * label - v1;
        float merged = WIDTH - qrWidth - label;

        float h = 22;
        cell(c, LEFT, top, label, h, "성명", true);
        cell(c, LEFT + label, top, v1, h, id.name(), false);
        cell(c, LEFT + label + v1, top, label, h, "출생연도", true);
        cell(c, LEFT + 2 * label + v1, top, v2, h, id.birthYear() + "년", false);

        float y = top + h;
        cell(c, LEFT, y, label, h, "발급번호", true);
        cell(c, LEFT + label, y, v1, h, id.number(), false);
        cell(c, LEFT + label + v1, y, label, h, "발급일", true);
        cell(c, LEFT + 2 * label + v1, y, v2, h, DATE.format(id.issuedOn()), false);

        y += h;
        cell(c, LEFT, y, label, h, "유효기간", true);
        cell(c, LEFT + label, y, merged, h, DATE.format(id.validUntil()) + "까지 (발급일부터 90일)", false);

        y += h;
        String consent = "본인이 직접 발급했으며, 카드 발급 심사 참고용 제출에 동의함 (" + DATE.format(id.issuedOn()) + ")";
        float consentHeight = cellHeight(c, merged, consent, h);
        cell(c, LEFT, y, label, consentHeight, "본인 동의", true);
        cell(c, LEFT + label, y, merged, consentHeight, consent, false);

        float bottom = y + consentHeight;
        float qrX = LEFT + WIDTH - qrWidth;
        c.box(qrX, top, qrWidth, bottom - top, null, 0.6f);
        float qrSize = 64;
        c.qr(qrUrl, qrX + (qrWidth - qrSize) / 2, top + (bottom - top - qrSize - 14) / 2, qrSize);
        c.centered(qrX + qrWidth / 2, top + (bottom - top - qrSize - 14) / 2 + qrSize + 11, 8, "진위 확인", false);
        return bottom;
    }

    // ── 2. 기록 개요 ──

    private static float overviewTable(PdfCanvas c, CertificateReport r, float top) throws IOException {
        float label = 100;
        float value = WIDTH - label;
        long percent = r.totalSettlements() == 0 ? 0 : Math.round(100.0 * r.verifiedCount() / r.totalSettlements());
        String[][] rows = {
                {"관찰 기간", MONTH.format(r.observedFrom()) + " ~ " + MONTH.format(r.observedTo()) + " (" + r.verifiedCount() + "개월)"},
                {"검증된 기록 비율", percent + "% (검증된 정산 " + r.verifiedCount() + "회 / 전체 정산 " + r.totalSettlements() + "회)"},
                {"월 이용 규모", "월 평균 " + CertificateReportCalculator.usageBand(r.averageBilled()) + " 구간 (금액은 구간으로만 표시)"},
                {"발급 요건", "검증된 정산 " + CertificateEligibilityService.REQUIRED_SETTLEMENTS + "회 이상 — 충족"}};

        float y = top;
        for (String[] row : rows) {
            float h = 22;
            cell(c, LEFT, y, label, h, row[0], true);
            cell(c, LEFT + label, y, value, h, row[1], false);
            y += h;
        }
        return y;
    }

    // ── 3. 결제일 정산 기록 ──

    private static float recordTable(PdfCanvas c, CertificateReport r, float top) throws IOException {
        float[] w = {100, 108, 100, WIDTH - 308};
        CertificateReport.Stats all = r.all();
        CertificateReport.Stats recent = r.recent();

        float y = top;
        y += row(c, y, w, 22, true, "항목", "전체 기간 (" + all.months() + "개월)", "최근 12개월", "뜻");
        y += row(c, y, w, 26, false, "결제일 완납률", rate(all.fullPaid(), all.months(), "개월"), rate(recent.fullPaid(), recent.months(), "개월"),
                "결제일에 청구액을 모두 정산한 달의 비율");
        y += row(c, y, w, 26, false, "최장 연속 완납", all.maxStreak() + "개월", recent.maxStreak() + "개월", "결제일 완납이 이어진 가장 긴 기간");
        y += row(c, y, w, 26, false, "결제일 여유율", marginRate(all), marginRate(recent), "(결제일 잔액 - 청구액) ÷ 결제일 잔액");
        y += row(c, y, w, 26, false, "부족분 해소율", resolveRate(all), resolveRate(recent), "모자란 금액을 직접 보내 스스로 해소한 비율");
        y += row(c, y, w, 26, false, "평균 해소 기간", resolveDays(all), resolveDays(recent), "결제일부터 부족분을 보낼 때까지 걸린 날 수");
        return y;
    }

    /** 표 한 줄. 첫 칸은 항목 이름(회색), 줄 높이는 글이 길어지면 늘어난다. 높이를 돌려준다 */
    private static float row(PdfCanvas c, float y, float[] widths, float minHeight, boolean header, String... texts) throws IOException {
        float h = minHeight;
        for (int i = 0; i < texts.length; i++) h = Math.max(h, cellHeight(c, widths[i], texts[i], minHeight));
        float x = LEFT;
        for (int i = 0; i < texts.length; i++) {
            boolean label = header || i == 0; // 머리 줄과 첫 칸(항목 이름)은 회색 바탕에 굵게
            cell(c, x, y, widths[i], h, texts[i], label, label ? Align.CENTER_BOLD : i == 3 ? Align.LEFT : Align.CENTER);
            x += widths[i];
        }
        return h;
    }

    private static String rate(int part, int whole, String unit) {
        return whole == 0 ? "해당 없음" : Math.round(100.0 * part / whole) + "% (" + part + "/" + whole + unit + ")";
    }

    private static String marginRate(CertificateReport.Stats s) {
        return String.format(Locale.KOREA, "평균 %.1f%%", s.avgMarginRate());
    }

    private static String resolveRate(CertificateReport.Stats s) {
        return s.shortfallCount() == 0 ? "해당 없음(부족 0건)" : rate(s.selfResolved(), s.shortfallCount(), "건");
    }

    private static String resolveDays(CertificateReport.Stats s) {
        return s.avgResolveDays() == null ? "해당 없음" : String.format(Locale.KOREA, "%.1f일", s.avgResolveDays());
    }

    // ── 4. 읽는 분께, 맺음 ──

    private static void notes(PdfCanvas c, float top) throws IOException {
        float y = top;
        for (int i = 0; i < NOTES.size(); i++) {
            c.text(LEFT + 4, y + 9, BODY, (i + 1) + ".", false);
            for (String line : c.wrap(NOTES.get(i), WIDTH - 26, BODY)) {
                c.text(LEFT + 20, y + 9, BODY, line, false);
                y += LINE;
            }
            y += 3;
        }
    }

    private static void footer(PdfCanvas c, LocalDate issuedOn) throws IOException {
        float base = PAGE_HEIGHT - 70;
        c.centered(CENTER, base, 11, LONG_DATE.format(issuedOn), false);
        c.right(RIGHT, base, 11, "발급  첫단추");
        c.hline(LEFT, RIGHT, PAGE_HEIGHT - 52, 0.6f);
        c.centered(CENTER, PAGE_HEIGHT - 38, 8, "※ 시연용 문서입니다. 모든 수치는 합성 데이터이며 실제 개인의 기록이 아닙니다.", false);
    }

    // ── 공통 ──

    private enum Align { LEFT, CENTER, CENTER_BOLD }

    private static float heading(PdfCanvas c, String title, float top) throws IOException {
        c.text(LEFT, top + 11, 12, title, true);
        return top + 20;
    }

    private static float cellHeight(PdfCanvas c, float width, String text, float minHeight) throws IOException {
        int lines = c.wrap(text, width - 2 * PAD, BODY).size();
        return Math.max(minHeight, lines * LINE + 2 * PAD - 2);
    }

    private static void cell(PdfCanvas c, float x, float top, float w, float h, String text, boolean label) throws IOException {
        cell(c, x, top, w, h, text, label, label ? Align.CENTER_BOLD : Align.LEFT);
    }

    /** 상자 하나와 그 안의 글. 길면 줄을 나누고 위아래 가운데에 놓는다 */
    private static void cell(PdfCanvas c, float x, float top, float w, float h, String text, boolean gray, Align align) throws IOException {
        c.box(x, top, w, h, gray ? LABEL_GRAY : null, 0.6f);
        List<String> lines = c.wrap(text, w - 2 * PAD, BODY);
        float textTop = top + (h - lines.size() * LINE) / 2;
        for (int i = 0; i < lines.size(); i++) {
            float base = textTop + i * LINE + 8.6f;
            if (align == Align.LEFT) c.text(x + PAD, base, BODY, lines.get(i), false);
            else c.centered(x + w / 2, base, BODY, lines.get(i), align == Align.CENTER_BOLD);
        }
    }

    private static PDFont loadFont(PDDocument doc) throws IOException {
        try (InputStream in = new ClassPathResource(FONT).getInputStream()) {
            return PDType0Font.load(doc, in); // 쓴 글자만 PDF 에 넣는다
        }
    }
}
