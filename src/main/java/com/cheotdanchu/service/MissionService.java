package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.entity.MissionSubmission;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.entity.SubmissionPhoto;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.MissionRepository;
import com.cheotdanchu.repository.MissionSubmissionRepository;
import com.cheotdanchu.repository.NotificationRepository;
import com.cheotdanchu.repository.SubmissionPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final SeedRepository seedRepository;

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

        Mission mission = missionRepository.save(Mission.createByParent(parent.getFamily(), req));

        memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD)
                .ifPresent(child -> notificationRepository.save(Notification.of(
                        child, Notification.Type.NEW_MISSION, "새 미션이 열렸어요", mission.getTitle(),
                        mission.getMissionId(), LocalDateTime.now())));

        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    public MissionDetailResponse getMission(Long personaId, Long missionId) {
        Member member = memberRepository.getPersona(personaId);
        Mission m = missionRepository.findById(missionId)
                // 다른 가족의 미션도 404: 존재 여부를 알려주지 않기 위해
                .filter(found -> found.getFamily().getFamilyId().equals(member.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.MISSION_NOT_FOUND));

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
