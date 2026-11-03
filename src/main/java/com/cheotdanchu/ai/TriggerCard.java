package com.cheotdanchu.ai;

import java.util.Map;

/**
 * 트리거가 만든 카드 한 장.
 *
 * trigger   : T-02, T-07 ... (트리거 명세의 ID)
 * audience  : "자녀" | "부모"
 * period    : 같은 카드를 두 번 만들지 않기 위한 기간 (정산 달, 용돈 달, 또는 "once")
 * date      : 카드가 생긴 날 (yyyy-MM-dd)
 * group     : 화면 위 순서. 1 안전 → 2 정산 결과 → 3 결정 → 4 회고·브리핑 → 5 안내
 * title/body: 화면에 보여줄 문장
 * extra     : 카드별 추가 값 (LLM 카드의 next_tip, reasons, D-6 카드의 금액 등)
 * textType  : "틀" | "LLM" | "틀(LLM 실패)" | "LLM 대기"(아직 문장을 안 만든 상태)
 * facts     : LLM 카드에 넘길 원장 숫자 (틀 카드는 비어 있음)
 */
public record TriggerCard(
        String trigger,
        String childId,
        String audience,
        String period,
        String date,
        int group,
        String title,
        String body,
        Map<String, Object> extra,
        String textType,
        Map<String, Object> facts) {

    public String key() {
        return childId + "|" + trigger + "|" + audience + "|" + period;
    }

    TriggerCard withText(String title, String body, Map<String, Object> extra, String textType) {
        return new TriggerCard(trigger, childId, audience, period, date, group, title, body, extra, textType, facts);
    }
}
