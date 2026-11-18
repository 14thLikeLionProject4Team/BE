package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MyDtos;
import com.cheotdanchu.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyController {

    private final MyPageService myPageService;

    @Operation(summary = "내 정보 조회 (자녀)",
            description = "자녀 전용(부모는 403). 이름·생년월일·만 나이(시연 기준일), 가족카드 연결 여부, 열람 범위, 성년 전환 여부를 보여줍니다. 만 19세가 되면 adultTransition.transitioned=true 이고 at 은 만 19세가 된 날입니다.")
    @GetMapping("/child/my")
    public ApiResponse<MyDtos.ChildResponse> getChildMy(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CHILD_MY_OK, myPageService.getChildMy(personaId));
    }

    @Operation(summary = "정보 조회 (부모)",
            description = "부모 전용(자녀는 403). 보호자 이름, 관계(\"{아이 이름} 보호자\"), 가족카드 본인회원 여부, 아이 선택 목록(지금은 아이 1명)을 보여줍니다.")
    @GetMapping("/parent/my")
    public ApiResponse<MyDtos.ParentResponse> getParentMy(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.PARENT_MY_OK, myPageService.getParentMy(personaId));
    }

    @Operation(summary = "약속 요약 조회 (부모)",
            description = "부모 전용(자녀는 403). \"{아이}와의 약속\" 카드: 연속 완납 배지(streakMonths), 월 용돈 현재·예정(allowance, 바뀔 예정이 없으면 next 는 null), 용돈 보내는 날·자동이체·결제일, 열람 범위, 미션 상한(비율·금액), 카드 한도(보기만). 아이가 만 19세가 되면 403(기록이 본인에게 넘어갔어요, 성년 전환 카드가 대신함). childId 는 선택(없으면 첫째 아이)이고 우리 가족의 아이가 아니면 404 입니다.")
    @GetMapping("/parent/promise")
    public ApiResponse<MyDtos.PromiseResponse> getPromise(
            @RequestHeader("X-Persona-Id") Long personaId, @RequestParam(required = false) Long childId) {
        return ApiResponse.onSuccess(SuccessStatus.PROMISE_OK, myPageService.getPromise(personaId, childId));
    }

    @Operation(summary = "성년 전환 카드 조회 (부모)",
            description = "부모 전용(자녀는 403). 아이가 만 19세가 되면 transitioned=true, at(전환일), togetherCount(전환일까지 함께한 결제일 횟수), recordsHidden=true 를 줍니다. 전환 뒤 기록은 본인만 보므로 숫자 기록은 주지 않습니다. 전환 전이면 transitioned=false 이고 나머지는 null/false. childId 는 선택(없으면 첫째 아이)이고 우리 가족의 아이가 아니면 404 입니다.")
    @GetMapping("/parent/adult-transition")
    public ApiResponse<MyDtos.AdultTransitionCard> getAdultTransition(
            @RequestHeader("X-Persona-Id") Long personaId, @RequestParam(required = false) Long childId) {
        return ApiResponse.onSuccess(SuccessStatus.ADULT_TRANSITION_OK, myPageService.getAdultTransition(personaId, childId));
    }
}
