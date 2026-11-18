package com.cheotdanchu.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 답을 화면에 보여주기 전에 하는 검사와, 고정 문장 모음이에요.
 */
public final class AnswerGuard {

    private AnswerGuard() {}

    public static final String REFUSAL = "그건 내가 가진 자료로는 정확히 답하기 어려워요.";
    public static final String OUT_OF_SCOPE = "그건 내가 도와주기 어려워요. 돈이나 첫단추에 대해 물어봐 주세요.";
    public static final String WORRY_FALLBACK = "이야기해 줘서 고마워요. 조금 더 말해 줄래요?";
    public static final String MY_NUMBERS_DEFAULT = "내 금액은 홈 화면에서 확인할 수 있어요.";
    public static final String CRISIS_REPLY =
            "지금 많이 힘든 것 같아요. 혼자 견디지 않아도 돼요.\n"
          + "청소년상담 1388(전화·문자·카카오톡)에서 지금 바로 이야기를 들어줄 수 있어요.\n"
          + "위험한 상황이면 112에 연락하세요.";

    /** 시작 목록이에요. 평가하면서 늘려 주세요. */
    public static final List<String> FORBIDDEN = List.of(
            "잘못", "실패", "나쁜 습관", "낭비", "혼나", "한심",
            "대출 받아", "빌려 봐", "빌리면 돼", "대리입금 해", "현금서비스 받아",
            "투자해", "사 두세요", "추천해요",
            "카드가 나와요", "카드가 발급돼요", "신용점수가 올라");

    /**
     * 위기 표현. 분류(LLM)보다 먼저 검사해서, LLM이 안 돼도 위기 안내가 나가게 해요.
     * 띄어쓰기를 빼고 비교해요. "협박"은 넣지 않아요 (대리입금 협박은 안전 카드가 답해요).
     */
    public static final List<String> CRISIS = List.of(
            "죽고싶", "죽어버리", "자살", "자해", "사라지고싶", "없어지고싶", "살기싫",
            "뛰어내리", "때려", "때렸", "폭행", "학대");

    private static final Pattern CONTACT = Pattern.compile("(?<!\\d)1\\d{2,3}(?!\\d)");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d{4,}");

    /** 금지어가 있으면 그 단어를, 없으면 null. */
    public static String forbiddenIn(String text) {
        if (text == null) return null;
        for (String w : FORBIDDEN) if (text.contains(w)) return w;
        return null;
    }

    /** 위기 표현이 있으면 true. */
    public static boolean isCrisis(String text) {
        if (text == null) return false;
        String t = text.replaceAll("\\s+", "");
        for (String w : CRISIS) if (t.contains(w)) return true;
        return false;
    }

    /** 1388, 1332, 112 같은 상담·신고 번호. */
    public static Set<String> contactsIn(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = CONTACT.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** 글에 나온 금액(1,000 이상) 중 facts에 없는 것. 비어 있으면 통과. */
    public static List<String> unknownAmounts(String text, String factsText) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            if (!factsText.contains(m.group())) out.add(m.group());
        }
        return out;
    }
}
