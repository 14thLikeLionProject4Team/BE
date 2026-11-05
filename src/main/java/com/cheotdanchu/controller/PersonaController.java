package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.PersonaResponse;
import com.cheotdanchu.service.PersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    @GetMapping("/personas")
    public ApiResponse<PersonaResponse> getPersonas() {
        return ApiResponse.onSuccess(SuccessStatus.PERSONA_LIST_OK, personaService.getPersonas());
    }
}
