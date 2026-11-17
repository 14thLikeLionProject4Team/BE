package com.cheotdanchu.common.util;

/** 이름 뒤에 붙는 조사(야/아, 가/이)를 고를 때 쓰는 한글 도우미 */
public final class KoreanText {

    private KoreanText() {}

    /** 마지막 글자가 받침 있는 한글이면 true. 빈 문자열이나 한글이 아닌 글자로 끝나면 false */
    public static boolean hasBatchim(String word) {
        if (word == null || word.isEmpty()) return false;
        char last = word.charAt(word.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
    }
}
