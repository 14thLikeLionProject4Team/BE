package com.cheotdanchu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 부족 예상 점검 카드의 화면 문구. 표시 규칙(SettlementService)과 분리해서 문구만 따로 바꿀 수 있게 한다 */
final class ShortfallCheckCopy {

    private ShortfallCheckCopy() {}

    static String message(long gap) {
        return "지금 속도면 결제일에 " + won(gap) + "원이 모자랄 것 같아요";
    }

    /** 남은 돈으로 메울 수 있으면 그 사실을, 일부만 메울 수 있으면 일부라고, 남은 돈이 없으면 그 줄은 뺀다 */
    static List<String> guides(long gap, long carryover) {
        List<String> guides = new ArrayList<>();
        if (carryover >= gap) guides.add("지난달까지 남은 돈 " + won(carryover) + "원으로 갚을 수 있어요");
        else if (carryover > 0) guides.add("지난달까지 남은 돈 " + won(carryover) + "원으로 일부 갚을 수 있어요");
        guides.add("다음 용돈 전까지 직접 보내도 돼요");
        guides.add("다른 사람에게 빌리지 마세요");
        return guides;
    }

    private static String won(long amount) {
        return String.format(Locale.KOREA, "%,d", amount);
    }
}
