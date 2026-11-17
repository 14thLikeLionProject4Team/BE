package com.cheotdanchu.service;

import com.cheotdanchu.dto.RecordsSummaryResponse;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 확인서 PDF 를 만든다. 한글이 나오도록 나눔고딕(src/main/resources/fonts, SIL OFL)을 PDF 에 넣는다 */
@Component
public class CertificatePdfWriter {

    private static final String FONT = "fonts/NanumGothic.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA);
    private static final float LEFT = 70;

    public byte[] write(String name, RecordsSummaryResponse r, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont font = loadFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 740;
                text(cs, font, 26, LEFT, y, "금융행동 기록 확인서");
                y -= 20;
                line(cs, y);
                y -= 40;
                text(cs, font, 13, LEFT, y, "이름  " + name);
                y -= 24;
                text(cs, font, 13, LEFT, y, "발급일  " + DATE.format(issuedAt));
                y -= 24;
                text(cs, font, 13, LEFT, y, "유효기한  " + DATE.format(expiresAt) + "까지");
                y -= 50;

                text(cs, font, 15, LEFT, y, "결제일 정산 기록");
                y -= 32;
                text(cs, font, 13, LEFT, y, String.format(Locale.KOREA, "결제일 완납    %d / %d개월", r.fullPaidMonths(), r.totalMonths()));
                y -= 26;
                text(cs, font, 13, LEFT, y, String.format(Locale.KOREA, "최장 연속 완납    %d개월", r.maxStreak()));
                y -= 26;
                text(cs, font, 13, LEFT, y, String.format(Locale.KOREA, "결제일 여유율 평균    %.1f%%", r.avgMarginRate()));
                y -= 26;
                text(cs, font, 13, LEFT, y, String.format(Locale.KOREA, "부족분 스스로 해소    %d / %d건", r.selfResolvedCount(), r.shortfallCount()));
                y -= 40;
                line(cs, y);
                y -= 30;
                text(cs, font, 10, LEFT, y, "이 확인서는 첫단추에 쌓인 결제일 정산 기록을 점수 없이 있는 그대로 담았어요.");
                y -= 16;
                text(cs, font, 10, LEFT, y, "부모 지원·현금 정산으로 제외된 정산은 기록에 들어가지 않아요.");
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("확인서 PDF 를 만들지 못했어요", e);
        }
    }

    private static PDFont loadFont(PDDocument doc) throws IOException {
        try (InputStream in = new ClassPathResource(FONT).getInputStream()) {
            return PDType0Font.load(doc, in); // 쓴 글자만 PDF 에 넣는다
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static void line(PDPageContentStream cs, float y) throws IOException {
        cs.moveTo(LEFT, y);
        cs.lineTo(PDRectangle.A4.getWidth() - LEFT, y);
        cs.stroke();
    }
}
