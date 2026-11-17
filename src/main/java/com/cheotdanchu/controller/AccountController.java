package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.AccountDtos;
import com.cheotdanchu.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "연결 계좌 조회",
            description = "자녀·부모 공용. 우리 가족의 계좌(자녀, 부모 순)를 은행명과 마스킹한 계좌번호로 보여줍니다. balance 는 호출한 사람 본인 계좌만 있고 상대 계좌는 null 입니다. 계좌 전체 잔액이라 지난달까지 남은 돈을 포함하며, 홈의 '이번 달 쓸 수 있는 돈'과 다른 값입니다.")
    @GetMapping("/accounts")
    public ApiResponse<AccountDtos.ListResponse> getAccounts(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.ACCOUNTS_OK, accountService.getAccounts(personaId));
    }
}
