package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Member;

import java.util.List;

public final class AccountDtos {

    private AccountDtos() {}

    public record ListResponse(List<Item> items) {}

    /** 계좌 하나. balance 는 호출한 사람 본인 계좌에만 있고 상대 계좌는 null */
    public record Item(
            Long accountId,
            Member.Role ownerRole,
            String bankName,
            String maskedAccountNumber,
            Integer balance) {}
}
