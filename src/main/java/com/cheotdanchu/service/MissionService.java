package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.config.PhotoStorage;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.dto.MissionSubmitResponse;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.entity.MissionSubmission;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.SubmissionPhoto;
import com.cheotdanchu.entity.Transfer;
import com.cheotdanchu.repository.AccountRepository;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.MissionRepository;
import com.cheotdanchu.repository.MissionSubmissionRepository;
import com.cheotdanchu.repository.NotificationRepository;
import com.cheotdanchu.repository.SettlementRepository;
import com.cheotdanchu.repository.SubmissionPhotoRepository;
import com.cheotdanchu.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private static final EnumSet<Mission.Status> ACTIVE =
            EnumSet.of(Mission.Status.NEW, Mission.Status.IN_PROGRESS, Mission.Status.SUBMITTED);

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;
    private final MissionSubmissionRepository submissionRepository;
    private final SubmissionPhotoRepository photoRepository;
    private final NotificationRepository notificationRepository;
    private final FamilySettingRepository familySettingRepository;
    private final AllowanceChangeRepository allowanceChangeRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final SettlementRepository settlementRepository;
    private final SeedRepository seedRepository;
    private final PhotoStorage photoStorage;

    public MissionListResponse getMissions(Long personaId, String status) {
        if (!"active".equals(status)) throw new GeneralException(ErrorStatus.INVALID_MISSION_STATUS);

        Member member = memberRepository.getPersona(personaId);

        LocalDate today = seedRepository.today();
        List<MissionListResponse.MissionDto> missions = missionRepository
                .findByFamily_FamilyIdAndStatusInOrderByMissionId(member.getFamily().getFamilyId(), ACTIVE)
                .stream()
                .map(m -> toDto(m, today))
                .toList();

        int totalReward = missions.stream()
                .map(MissionListResponse.MissionDto::rewardAmount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return new MissionListResponse(missions.size(), totalReward, missions);
    }

    @Transactional
    public MissionCreateResponse createMission(Long personaId, MissionCreateRequest req) {
        Member parent = memberRepository.getPersona(personaId);
        if (parent.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        if (req.endDate().isBefore(req.startDate())) throw new GeneralException(ErrorStatus.INVALID_PERIOD);

        Long familyId = parent.getFamily().getFamilyId();
        Optional<Member> child = memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD);
        long remaining = remainingRewardCap(familyId, child);
        if (req.rewardAmount() > remaining) {
            throw new GeneralException(ErrorStatus.REWARD_CAP_EXCEEDED,
                    String.format(Locale.KOREA, "이번 달 남은 한도(%,d원)를 넘어요.", remaining));
        }

        Mission mission = missionRepository.save(Mission.createByParent(parent.getFamily(), req));

        child.ifPresent(c -> notificationRepository.save(Notification.of(
                c, Notification.Type.NEW_MISSION, "새 미션이 열렸어요", mission.getTitle(),
                mission.getMissionId(), LocalDateTime.now())));

        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    /**
     * 미션 보상 남은 한도 = 이번 정산 기간 용돈 × 미션 상한 비율 − 현재 정산 기간에 받은 미션 보상.
     * 명세대로 지급된 보상만 뺀다 (진행 중인 미션은 빼지 않음). 실제 초과 지급은 승인(지급) 시점에 막는다.
     * 용돈 설정이 없으면 한도 없음, 진행 중인 정산이 없으면 받은 보상 0.
     */
    private long remainingRewardCap(Long familyId, Optional<Member> child) {
        FamilySetting setting = familySettingRepository.findById(familyId).orElse(null);
        if (setting == null) return Long.MAX_VALUE;

        LocalDate today = seedRepository.today();
        Optional<Settlement> current = settlementRepository.findCurrent(familyId, today);
        // 용돈은 정산 기간 끝나는 달에 들어온다 (9/26~10/25 기간 → 10월 용돈). 10/26~10/31이면 11월 용돈 기준
        YearMonth month = YearMonth.from(current.map(Settlement::getPeriodEnd).orElse(today));
        Integer allowance = allowanceChangeRepository.findForMonth(familyId, month, setting.getAllowanceDay())
                .map(AllowanceChange::getAmount)
                .orElse(null);
        if (allowance == null) return Long.MAX_VALUE;

        long cap = (long) allowance * setting.getMissionCapRate() / 100;
        long received = current.isEmpty() ? 0L : child.flatMap(c -> accountRepository.findByMemberId(c.getMemberId()))
                .map(account -> transferRepository.sumReceived(account.getAccountId(), Transfer.Type.MISSION_REWARD,
                        current.get().getPeriodStart().atStartOfDay(),
                        current.get().getPeriodEnd().plusDays(1).atStartOfDay()))
                .orElse(0L);
        return Math.max(cap - received, 0);
    }

    @Transactional
    public MissionSubmitResponse submitMission(Long personaId, Long missionId, List<MultipartFile> photos) {
        Member child = memberRepository.getPersona(personaId);
        if (child.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.CHILD_ONLY);
        Mission mission = familyMission(child, missionId);
        if (!mission.canSubmit(seedRepository.today())) throw new GeneralException(ErrorStatus.MISSION_NOT_SUBMITTABLE);

        List<MultipartFile> files = photos == null ? List.of() : photos.stream().filter(f -> !f.isEmpty()).toList();
        if (mission.getVerifyMethod() == Mission.VerifyMethod.PHOTO && files.isEmpty()) {
            throw new GeneralException(ErrorStatus.PHOTO_REQUIRED);
        }
        files.forEach(photoStorage::validate);

        MissionSubmission submission = submissionRepository.save(MissionSubmission.of(mission, LocalDateTime.now()));
        files.forEach(f -> photoRepository.save(SubmissionPhoto.of(submission, photoStorage.save(f))));
        mission.submit();

        return new MissionSubmitResponse(submission.getSubmissionId(), mission.getStatus());
    }

    public MissionDetailResponse getMission(Long personaId, Long missionId) {
        Member member = memberRepository.getPersona(personaId);
        Mission m = familyMission(member, missionId);

        List<MissionSubmission> submissions = submissionRepository.findByMission_MissionIdOrderBySubmittedAtDesc(missionId);
        Map<Long, List<String>> photos = photoRepository
                .findBySubmission_SubmissionIdInOrderByPhotoId(submissions.stream().map(MissionSubmission::getSubmissionId).toList())
                .stream()
                .collect(Collectors.groupingBy(p -> p.getSubmission().getSubmissionId(),
                        Collectors.mapping(SubmissionPhoto::getImageUrl, Collectors.toList())));

        int done = (int) submissions.stream()
                .filter(s -> s.getReviewStatus() == MissionSubmission.ReviewStatus.APPROVED)
                .count();

        return new MissionDetailResponse(
                m.getMissionId(), m.getTitle(), m.getCriteria(), m.getMissionType(), m.getOrigin(), m.getStatus(),
                m.getRewardAmount(), m.getDesiredReward(), m.getStartDate(), m.getEndDate(),
                m.getRepeatType(), m.getVerifyMethod(),
                progress(m, done),
                submissions.stream()
                        .map(s -> new MissionDetailResponse.SubmissionDto(
                                s.getSubmissionId(), s.getReviewStatus(),
                                photos.getOrDefault(s.getSubmissionId(), List.of()), s.getSubmittedAt()))
                        .toList());
    }

    /** 내 가족의 미션. 다른 가족의 미션도 404: 존재 여부를 알려주지 않기 위해 */
    private Mission familyMission(Member member, Long missionId) {
        return missionRepository.findById(missionId)
                .filter(found -> found.getFamily().getFamilyId().equals(member.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.MISSION_NOT_FOUND));
    }

    private MissionListResponse.MissionDto toDto(Mission m, LocalDate today) {
        int done = (int) submissionRepository.countByMission_MissionIdAndReviewStatus(
                m.getMissionId(), MissionSubmission.ReviewStatus.APPROVED);

        return new MissionListResponse.MissionDto(
                m.getMissionId(), m.getTitle(), m.getMissionType(), m.getStatus(), m.getRewardAmount(),
                progress(m, done),
                description(m, today),
                m.getStartDate(), m.getEndDate());
    }

    private MissionListResponse.Progress progress(Mission m, int done) {
        int target = m.targetCount();
        return new MissionListResponse.Progress(done, target, Math.min(100, done * 100 / target));
    }

    private String description(Mission m, LocalDate today) {
        String until = m.getEndDate() == null ? "" : m.getEndDate().getMonthValue() + "월 " + m.getEndDate().getDayOfMonth() + "일까지";
        return switch (m.getStatus()) {
            case NEW -> today.equals(m.getStartDate()) ? "오늘 열었어요 · " + until : until;
            case SUBMITTED -> "부모님 확인 기다리는 중";
            default -> until;
        };
    }
}
