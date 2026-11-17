package com.cheotdanchu.controller;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정산 관련 테스트가 같이 쓰는 데이터. 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산(2026-10-25)은 시드에서 아직 정산 전이고 청구 79,500원이다.
 * 정산 ID 는 시드 적재 순서에 기대지 않고 조회해서 쓴다.
 */
final class SettlementFixture {

    private SettlementFixture() {}

    /** 가족 A 의 이번 정산 ID */
    static long openSettlement(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT settlement_id FROM settlements WHERE family_id = 1 AND period_end = '2026-10-25'", Long.class);
    }

    /** 이번 정산까지 끝나서(완납) 검증된 정산이 12회가 된 상태로 만든다 */
    static void completeCurrentSettlement(JdbcTemplate jdbc) {
        jdbc.update("UPDATE settlements SET status = 'COMPLETED', result = 'FULL_PAID', billed_amount = 79500, balance_at_due = 90000 "
                + "WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end");
    }

    /** 결제일 정산 이체를 넣는다 (루피 계좌 → 부모 계좌). at 은 이체 시각 */
    static void insertSettlementTransfer(JdbcTemplate jdbc, long settlementId, int amount, String at) {
        jdbc.update("INSERT INTO transfers (from_account_id, to_account_id, type, amount, settlement_id, transferred_at) "
                + "SELECT c.account_id, p.account_id, 'SETTLEMENT', ?, ?, ? "
                + "FROM accounts c, accounts p WHERE c.member_id = 2 AND p.member_id = 1", amount, settlementId, at);
    }

    /**
     * 이번 정산을 결제일이 지나 부족이 남은 정산(SHORTFALL)으로 만든다: 청구 79,500원 중 결제일에 70,000원만 이체돼 9,500원이 부족하다.
     * 시연 기준일이 10-19라서 결제일 이체를 그보다 앞선 시각으로 둔다 (갚은 이체가 결제일 이체보다 뒤에 오도록).
     */
    static long overdueSettlement(JdbcTemplate jdbc) {
        long id = openSettlement(jdbc);
        jdbc.update("UPDATE settlements SET status = 'SHORTFALL', billed_amount = 79500 WHERE settlement_id = ?", id);
        insertSettlementTransfer(jdbc, id, 70000, "2026-10-18 18:00:00");
        return id;
    }
}
