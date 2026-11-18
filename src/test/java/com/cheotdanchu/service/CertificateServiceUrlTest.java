package com.cheotdanchu.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CertificateServiceUrlTest {

    @Test
    void 마지막_슬래시가_없으면_붙이고_있으면_그대로_둔다() {
        assertEquals("https://host/verify/", CertificateService.withTrailingSlash("https://host/verify"));
        assertEquals("https://host/verify/", CertificateService.withTrailingSlash("https://host/verify/"));
    }

    @Test
    void 붙은_주소는_발급번호와_이어도_깨지지_않는다() {
        assertEquals("https://host/verify/CD-2026-1019-0001", CertificateService.withTrailingSlash("https://host/verify") + "CD-2026-1019-0001");
    }
}
