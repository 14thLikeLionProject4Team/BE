package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.service.CertificateEligibilityService;
import com.cheotdanchu.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChildCertificateController {

    private final CertificateEligibilityService eligibilityService;
    private final CertificateService certificateService;

    @Operation(summary = "발급 요건 조회",
            description = "자녀 전용(부모는 403). 확인서 발급 요건 두 가지: 만 19세(ageReached, birthdayDDay)와 검증된 결제일 정산 12회(settlementCount/settlementRequired, 요건을 넘으면 12로 표시). 제외된 정산은 세지 않습니다. 둘 다 채워지면 canIssue=true 이고 availableFrom 은 null, 아니면 두 요건이 모두 채워지는 날(정산은 한 달에 하나씩 끝난다고 본 추정)입니다.")
    @GetMapping("/child/certificates/eligibility")
    public ApiResponse<CertificateDtos.EligibilityResponse> getEligibility(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_ELIGIBILITY_OK, eligibilityService.getEligibility(personaId));
    }

    @Operation(summary = "확인서 미리보기",
            description = "자녀 전용(부모는 403). 확인서에 들어갈 지표 4개(결제일 완납 fullPaidMonths/totalMonths, 최장 연속 완납 maxStreak, 결제일 여유율 평균 avgMarginRate, 부족분 스스로 해소 selfResolvedCount/shortfallCount)를 점수 없이 원값으로 보여줍니다. 기록 지표 조회(/records/summary)와 같은 계산이고, 발급 요건을 채우지 않아도 볼 수 있습니다.")
    @GetMapping("/child/certificates/preview")
    public ApiResponse<RecordsSummaryResponse> getPreview(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_PREVIEW_OK, certificateService.getPreview(personaId));
    }

    @Operation(summary = "확인서 최신 조회",
            description = "자녀 전용(부모는 403). 가장 최근에 발급한 확인서의 지표 4개와 발급·만료 시각, 만료 여부(expired)를 보여줍니다. 만료된 확인서도 내려주고 expired=true 로 알립니다. 발급한 적이 없으면 404.")
    @GetMapping("/child/certificates/latest")
    public ApiResponse<CertificateDtos.LatestResponse> getLatest(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_LATEST_OK, certificateService.getLatest(personaId));
    }
}
