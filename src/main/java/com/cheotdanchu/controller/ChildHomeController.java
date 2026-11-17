package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.ChildHomeDtos;
import com.cheotdanchu.service.ChildHomeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChildHomeController {

    private final ChildHomeService childHomeService;

    @Operation(summary = "자녀 홈 조회",
            description = "자녀 전용(부모는 403). 오늘 날짜, 상태 칩 값(streakMonths 연속 완납 개월, certificateDDay 확인서 발급 가능일까지 남은 날·이미 발급 가능하면 null·아직인데 추정일이 지났으면 0, visibility 열람 범위), 이번 정산(현재 정산 조회와 같은 값), 부족 예상 점검 카드를 보일지. 진행 중인 정산이 없으면 settlement 는 null 이고 shortfallCheckShow 는 false 입니다.")
    @GetMapping("/child/home")
    public ApiResponse<ChildHomeDtos.HomeResponse> getHome(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CHILD_HOME_OK, childHomeService.getHome(personaId));
    }

    @Operation(summary = "일별 지출 조회",
            description = "자녀 전용(부모는 403). 이번 정산 기간 시작일부터 오늘까지 하루씩 사용액(취소는 합산, 결제 없는 날은 0, 다른 날 결제를 취소하면 마이너스일 수 있음)과 이번 달 용돈(부모 홈 카드와 같은 기준). 오늘 이후 날짜는 없습니다. 진행 중인 정산이 없으면 data 가 null 입니다.")
    @GetMapping("/child/home/daily-spending")
    public ApiResponse<ChildHomeDtos.DailySpendingResponse> getDailySpending(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.DAILY_SPENDING_OK, childHomeService.getDailySpending(personaId));
    }
}
