package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.service.CertificateEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChildCertificateController {

    private final CertificateEligibilityService eligibilityService;

    @Operation(summary = "발급 요건 조회",
            description = "자녀 전용(부모는 403). 확인서 발급 요건 두 가지: 만 19세(ageReached, birthdayDDay)와 검증된 결제일 정산 12회(settlementCount/settlementRequired, 요건을 넘으면 12로 표시). 제외된 정산은 세지 않습니다. 둘 다 채워지면 canIssue=true 이고 availableFrom 은 null, 아니면 두 요건이 모두 채워지는 날(정산은 한 달에 하나씩 끝난다고 본 추정)입니다.")
    @GetMapping("/child/certificates/eligibility")
    public ApiResponse<CertificateDtos.EligibilityResponse> getEligibility(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_ELIGIBILITY_OK, eligibilityService.getEligibility(personaId));
    }
}
