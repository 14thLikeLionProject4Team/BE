package com.cheotdanchu.service;

/**
 * 계좌번호 마스킹: 숫자만 가리고 하이픈은 그대로 둔다. 뒤 4자리는 항상 보이고, 앞 4자리는 숫자가 12자리 이상으로 충분히 길 때만 보인다
 * (짧은 번호에서 앞뒤를 다 보여주면 가려지는 숫자가 너무 적다).
 */
final class AccountNumberMask {

    private static final int VISIBLE = 4;
    private static final int FRONT_VISIBLE_MIN_DIGITS = 12;

    private AccountNumberMask() {}

    static String mask(String accountNumber) {
        int digits = (int) accountNumber.chars().filter(Character::isDigit).count();
        boolean showFront = digits >= FRONT_VISIBLE_MIN_DIGITS;

        StringBuilder masked = new StringBuilder();
        int index = 0;
        for (char c : accountNumber.toCharArray()) {
            if (!Character.isDigit(c)) {
                masked.append(c);
                continue;
            }
            boolean visible = index >= digits - VISIBLE || (showFront && index < VISIBLE);
            masked.append(visible ? c : '*');
            index++;
        }
        return masked.toString();
    }
}
