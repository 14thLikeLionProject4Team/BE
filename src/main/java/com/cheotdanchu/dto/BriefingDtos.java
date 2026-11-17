package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Briefing;

import java.time.LocalDateTime;
import java.util.List;

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

    /** nextCursor: 다음 페이지를 부를 때 보낼 대상 달 (마지막이면 null) */
    public record ListResponse(long totalCount, List<Item> briefings, String nextCursor) {}

    /** 목록에서는 첫 줄(쓴 돈)만 보여준다 */
    public record Item(Long briefingId, String targetMonth, String firstLine, boolean isNew) {
        public static Item of(Briefing b) {
            return new Item(b.getBriefingId(), b.getTargetMonth(), b.getSpentLine(), b.getReadAt() == null);
        }
    }

    /** 다섯 줄: 쓴 돈, 남긴 돈, 달라진 점, 잘한 점, 같이 이야기할 것 */
    public record Lines(String spent, String saved, String changed, String good, String together) {}
}
