package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Briefing;

import java.time.LocalDateTime;

/** 월간 브리핑 응답 */
public final class BriefingDtos {

    private BriefingDtos() {}

    /** isNew: 아직 읽지 않음 ("새로 도착") */
    public record Detail(Long briefingId, String targetMonth, boolean isNew, Lines lines, LocalDateTime createdAt) {
        public static Detail of(Briefing b) {
            return new Detail(b.getBriefingId(), b.getTargetMonth(), b.getReadAt() == null,
                    new Lines(b.getSpentLine(), b.getSavedLine(), b.getChangedLine(), b.getGoodLine(), b.getTogetherLine()),
                    b.getCreatedAt());
        }
    }

    /** 다섯 줄: 쓴 돈, 남긴 돈, 달라진 점, 잘한 점, 같이 이야기할 것 */
    public record Lines(String spent, String saved, String changed, String good, String together) {}
}
