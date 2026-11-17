package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.MyDtos;
import com.cheotdanchu.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
}
