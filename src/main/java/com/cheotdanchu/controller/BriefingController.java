package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.BriefingDtos;
import com.cheotdanchu.service.BriefingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @Operation(summary = "최신 브리핑 조회", description = "부모 전용. 가장 최근 월간 브리핑의 다섯 줄(쓴 돈·남긴 돈·달라진 점·잘한 점·같이 이야기할 것). isNew = 아직 읽지 않음. 브리핑이 없으면 data null. 아이가 만 19세가 된 뒤에는 403.")
    @GetMapping("/briefings/latest")
    public ApiResponse<BriefingDtos.Detail> getLatest(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.BRIEFING_OK, briefingService.getLatest(personaId));
    }

    @Operation(summary = "브리핑 목록", description = "부모 전용. 지난 브리핑 최신순, 12개씩(첫 줄만). cursor는 이전 페이지의 nextCursor. 아이가 만 19세가 된 뒤에는 403.")
    @GetMapping("/briefings")
    public ApiResponse<BriefingDtos.ListResponse> getBriefings(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) Long cursor) {
        return ApiResponse.onSuccess(SuccessStatus.BRIEFING_LIST_OK, briefingService.getBriefings(personaId, cursor));
    }

    @Operation(summary = "브리핑 읽음 처리", description = "부모 전용. 읽은 브리핑은 isNew=false가 되어 화면에서 첫 줄만 남고 접힌다. 다른 가족의 브리핑 404, 이미 읽었어도 200.")
    @PatchMapping("/briefings/{briefingId}/read")
    public ApiResponse<Void> markRead(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long briefingId) {
        briefingService.markRead(personaId, briefingId);
        return ApiResponse.onSuccess(SuccessStatus.BRIEFING_READ, null);
    }
}
