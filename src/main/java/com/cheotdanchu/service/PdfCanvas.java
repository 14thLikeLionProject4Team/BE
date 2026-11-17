package com.cheotdanchu.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 한 쪽에 글자·상자·선·QR 을 그리는 작은 도구. 좌표는 쪽 위에서부터 아래로 잰 값(point)이라 위에서 아래로 읽는 순서대로 쓸 수 있다.
 * 굵은 글씨는 같은 글꼴을 테두리까지 칠해서 만든다(글꼴 파일 하나만 쓰기 때문).
 */
final class PdfCanvas {

    private final PDPageContentStream cs;
    private final PDFont font;
    private final float pageHeight;

    PdfCanvas(PDPageContentStream cs, PDFont font, float pageHeight) {
        this.cs = cs;
        this.font = font;
        this.pageHeight = pageHeight;
    }

    float width(String text, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** 글자 하나하나를 넓이로 재서 maxWidth 안에 들어가게 줄을 나눈다. 띄어쓰기에서 먼저 나누고, 단어가 한 줄보다 길면 글자 사이에서 나눈다 */
    List<String> wrap(String text, float maxWidth, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lastSpace = -1;
        for (char c : text.toCharArray()) {
            line.append(c);
            if (c == ' ') lastSpace = line.length() - 1;
            if (width(line.toString(), size) > maxWidth && line.length() > 1) {
                int cut = lastSpace > 0 ? lastSpace : line.length() - 1;
                lines.add(line.substring(0, cut).stripTrailing());
                String rest = line.substring(lastSpace > 0 ? cut + 1 : cut);
                line = new StringBuilder(rest);
                lastSpace = -1;
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** baselineTop: 글자 바닥선의 쪽 위에서부터의 거리 */
    void text(float x, float baselineTop, float size, String text, boolean bold) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        if (bold) {
            cs.setRenderingMode(RenderingMode.FILL_STROKE);
            cs.setLineWidth(size * 0.05f);
        }
        cs.newLineAtOffset(x, pageHeight - baselineTop);
        cs.showText(text);
        if (bold) cs.setRenderingMode(RenderingMode.FILL);
        cs.endText();
    }

    /** 가운데 맞춤 */
    void centered(float centerX, float baselineTop, float size, String text, boolean bold) throws IOException {
        text(centerX - width(text, size) / 2, baselineTop, size, text, bold);
    }

    /** 오른쪽 맞춤 */
    void right(float rightX, float baselineTop, float size, String text) throws IOException {
        text(rightX - width(text, size), baselineTop, size, text, false);
    }

    /** 상자. gray 는 0(검정)~1(흰색)이고 null 이면 채우지 않는다 */
    void box(float x, float top, float w, float h, Float gray, float lineWidth) throws IOException {
        if (gray != null) {
            cs.setNonStrokingColor(gray);
            cs.addRect(x, pageHeight - top - h, w, h);
            cs.fill();
            cs.setNonStrokingColor(0f);
        }
        cs.setLineWidth(lineWidth);
        cs.addRect(x, pageHeight - top - h, w, h);
        cs.stroke();
    }

    void hline(float x1, float x2, float top, float lineWidth) throws IOException {
        cs.setLineWidth(lineWidth);
        cs.moveTo(x1, pageHeight - top);
        cs.lineTo(x2, pageHeight - top);
        cs.stroke();
    }

    /** QR 코드를 size 크기의 정사각형 안에 그린다 (사각형을 모아 한 번에 칠한다) */
    void qr(String content, float x, float top, float size) throws IOException {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        } catch (WriterException e) {
            throw new IllegalStateException("QR 코드를 만들지 못했어요", e);
        }
        float cell = size / matrix.getWidth();
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int col = 0; col < matrix.getWidth(); col++) {
                if (matrix.get(col, row)) cs.addRect(x + col * cell, pageHeight - top - (row + 1) * cell, cell, cell);
            }
        }
        cs.fill();
    }
}
