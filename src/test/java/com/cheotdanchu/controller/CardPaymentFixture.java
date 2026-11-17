package com.cheotdanchu.controller;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 내역 테스트가 같이 쓰는 데이터. 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산(2026-09-26~10-25)에 카드 결제를 넣는다.
 * 정산 ID 는 시드 적재 순서에 기대지 않고 조회해서 쓴다.
 */
final class CardPaymentFixture {

    private CardPaymentFixture() {}

    static long currentSettlementId(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end", Long.class);
    }

    /** risk 는 위험 결제 유형(HIGH_AMOUNT 등)이고 아니면 null */
    static void insert(JdbcTemplate jdbc, String merchant, String category, int amount, boolean canceled, String risk, String paidAt) {
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, risk_type, paid_at) "
                + "VALUES (2, ?, ?, ?, ?, ?, ?, ?)", currentSettlementId(jdbc), merchant, category, amount, canceled, risk, paidAt);
    }

    /** 취소(마이너스) 결제를 넣는다 */
    static void insertCanceled(JdbcTemplate jdbc, String category, int amount, String paidAt) {
        insert(jdbc, "취소 가맹점", category, amount, true, null, paidAt);
    }
}
