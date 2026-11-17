package com.cheotdanchu.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountNumberMaskTest {

    @Test
    void 열_자리_번호는_뒤_네_자리만_보인다() {
        assertEquals("****-**-4567", AccountNumberMask.mask("3333-01-4567"));
    }

    @Test
    void 열두_자리_이상이면_앞_네_자리도_보이고_명세_예시와_같은_모양이다() {
        assertEquals("1002-***-**4821", AccountNumberMask.mask("1002-123-454821"));
    }

    @Test
    void 하이픈이_없어도_가린다_열두_자리() {
        assertEquals("1101****6789", AccountNumberMask.mask("110123456789"));
    }

    @Test
    void 여덟_자리_이하는_뒤_네_자리만_보인다() {
        assertEquals("****5678", AccountNumberMask.mask("12345678"));
        assertEquals("1234", AccountNumberMask.mask("1234")); // 4자리뿐이면 가릴 숫자가 없다
    }
}
