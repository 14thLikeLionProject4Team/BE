package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.ChildSpendingResponse;
import com.cheotdanchu.service.ChildSpendingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParentController {

    private final ChildSpendingService childSpendingService;

    @Operation(summary = "정산 요약 조회 (부모 홈)",
            description = "부모 전용. 부모 홈 '이번 정산 기간' 카드: 결제일·D-day·사용 총액(총액만, 가맹점·목록 없음)·이번 달 용돈 입금 확인. 부족 예상은 부모에게 보여주지 않습니다. 아이가 만 19세가 된 뒤에는 403(기록이 본인에게 넘어갔어요). 진행 중인 정산이 없으면 에러가 아니라 날짜 null, 총액 0.")
    @GetMapping("/parent/child-spending")
    public ApiResponse<ChildSpendingResponse> getChildSpending(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CHILD_SPENDING_OK, childSpendingService.getChildSpending(personaId));
    }
}
