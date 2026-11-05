package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.PersonaResponse;
import com.cheotdanchu.service.PersonaService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    @Operation(summary = "페르소나 목록 조회", description = "시작화면용. 가족별 페르소나 4명과 상태 칩을 반환합니다. 헤더 불필요.")
    @GetMapping("/personas")
    public ApiResponse<PersonaResponse> getPersonas() {
        return ApiResponse.onSuccess(SuccessStatus.PERSONA_LIST_OK, personaService.getPersonas());
    }
}
