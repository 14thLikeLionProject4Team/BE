package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.service.CertificateEligibilityService;
import com.cheotdanchu.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(summary = "확인서 발급",
            description = "자녀 전용(부모는 403). 발급 요건(만 19세, 검증된 결제일 정산 12회)을 모두 채웠을 때 그때의 지표로 PDF 확인서를 만들어 저장합니다. 발급 후 90일 동안 유효합니다. identityVerified 가 false 면 400, 요건을 못 채웠으면 409(\"11월 2일부터 발급할 수 있어요\"). 본인 인증은 시연에서 화면 단계로만 두고 서버는 결과값만 받습니다. 발급 시각은 시연 기준일의 날짜에 현재 시각을 붙입니다.")
    @PostMapping("/child/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CertificateDtos.IssueResponse> issue(
            @RequestHeader("X-Persona-Id") Long personaId,
            @Valid @RequestBody CertificateDtos.IssueRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_ISSUED, certificateService.issue(personaId, request.identityVerified()));
    }

    @Operation(summary = "확인서 다운로드",
            description = "자녀 전용(부모는 403). 본인이 발급한 확인서를 PDF 파일(application/pdf, certificate_{id}.pdf)로 내려받습니다. 남의 확인서나 없는 확인서는 404, 만료된 확인서는 410(\"만료된 확인서예요\"), 서버에서 PDF 파일을 찾을 수 없으면 404.")
    @GetMapping("/child/certificates/{certificateId}/download")
    public ResponseEntity<byte[]> download(@RequestHeader("X-Persona-Id") Long personaId, @PathVariable Long certificateId) {
        CertificateDtos.DownloadFile file = certificateService.download(personaId, certificateId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // 개인 정산 기록이라 중간 캐시·브라우저에 남기지 않는다
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @Operation(summary = "확인서 공유 링크 생성",
            description = "자녀 전용(부모는 403). 본인의 유효한 확인서에 기한이 있는 공유 링크를 만듭니다. expiresInHours(1~168)만큼 유효하고 확인서 유효기한을 넘길 수 없습니다(400). 남의 확인서·없는 확인서는 404, 만료된 확인서는 410. 확인서마다 링크는 하나라서 다시 만들면 이전 링크는 못 씁니다. 링크를 받은 사람이 여는 화면은 이번 범위에서 제외입니다.")
    @PostMapping("/child/certificates/{certificateId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CertificateDtos.ShareResponse> share(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long certificateId,
            @Valid @RequestBody CertificateDtos.ShareRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.CERTIFICATE_SHARED, certificateService.share(personaId, certificateId, request.expiresInHours()));
    }
}
