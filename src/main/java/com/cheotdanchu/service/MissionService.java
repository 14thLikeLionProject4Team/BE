package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.config.PhotoStorage;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.common.util.KoreanText;
import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionCreateResponse;
import com.cheotdanchu.dto.MissionDetailResponse;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.dto.MissionProposalRequest;
import com.cheotdanchu.dto.MissionUpdateRequest;
import com.cheotdanchu.dto.MissionQueryDtos;
import com.cheotdanchu.dto.MissionSubmitResponse;
import com.cheotdanchu.dto.SubmissionReviewDtos;
import com.cheotdanchu.entity.Account;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final EnumSet<Mission.Status> PROPOSALS = EnumSet.of(Mission.Status.PROPOSED, Mission.Status.ON_HOLD);
    private static final EnumSet<Mission.Status> ENDED =
            EnumSet.of(Mission.Status.COMPLETED, Mission.Status.CANCELED, Mission.Status.EXPIRED);
    private static final int ENDED_PAGE_SIZE = 20;
    private static final int RECOMMENDATION_PAGE_SIZE = 10;

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

    public MissionListResponse getActiveMissions(Long personaId) {
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
        Member parent = parent(personaId);
        if (req.endDate().isBefore(req.startDate())) throw new GeneralException(ErrorStatus.INVALID_PERIOD);

        Optional<Member> child = childOf(parent);
        checkRewardCap(parent, child, req.rewardAmount());
        Mission mission = missionRepository.save(Mission.createByParent(parent.getFamily(), req));
        notifyNewMission(child, mission);

        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    /** 아이의 미션 제안: PROPOSED로 저장하고 부모에게 알린다 */
    @Transactional
    public MissionCreateResponse proposeMission(Long personaId, MissionProposalRequest req) {
        Member child = child(personaId);
        if (req.endDate().isBefore(req.startDate())) throw new GeneralException(ErrorStatus.INVALID_PERIOD);

        Mission mission = missionRepository.save(Mission.createByChild(child.getFamily(), req));
        memberRepository.findByFamily_FamilyIdAndRole(child.getFamily().getFamilyId(), Member.Role.PARENT)
                .ifPresent(p -> notificationRepository.save(Notification.of(p, Notification.Type.MISSION_PROPOSED,
                        "아이가 미션을 제안했어요", mission.getTitle(), mission.getMissionId(), demoNow())));

        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    /** 제안·보류·추천 미션을 부모가 금액을 정해서 연다. 새로 만드는 것과 같은 검사(기간·한도)를 하고 아이에게 알린다 */
    @Transactional
    public MissionCreateResponse openMission(Long personaId, Long missionId, MissionCreateRequest req) {
        Member parent = parent(personaId);
        Mission mission = familyMissionForUpdate(parent, missionId);
        if (!mission.canOpen()) throw new GeneralException(ErrorStatus.MISSION_NOT_OPENABLE);
        if (req.endDate().isBefore(req.startDate())) throw new GeneralException(ErrorStatus.INVALID_PERIOD);

        Optional<Member> child = childOf(parent);
        checkRewardCap(parent, child, req.rewardAmount());
        mission.open(req);
        notifyNewMission(child, mission);

        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    /** 제안 보류 ("다음에"): PROPOSED → ON_HOLD */
    @Transactional
    public MissionCreateResponse holdMission(Long personaId, Long missionId) {
        Member parent = parent(personaId);
        Mission mission = familyMissionForUpdate(parent, missionId);
        if (!mission.canHold()) throw new GeneralException(ErrorStatus.MISSION_NOT_HOLDABLE);
        mission.hold();
        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    private static final String NOT_OPENED_YET = "아직 열지 않은 미션은 '열기'에서 정해 주세요.";

    /**
     * 진행 중인 미션 내용을 고친다 (보낸 필드만). 끝난 미션·아직 열지 않은 미션은 409.
     * 보상을 바꾸면 새로 만들 때와 같은 한도 검사를 하고, 아이가 낸 완료를 확인하기 전(SUBMITTED)에는 보상을 못 바꾼다
     * (확인 대기 중에 금액이 바뀌면 아이가 모르는 금액이 지급된다).
     * 마감일은 오늘보다, 시작일보다 앞설 수 없고, 마감일을 당겨서 이미 승인받은 횟수가 목표를 채우면 바로 완료로 바꾼다
     * (안 바꾸면 계속 제출·승인돼서 보상이 더 나간다).
     */
    @Transactional
    public MissionCreateResponse updateMission(Long personaId, Long missionId, MissionUpdateRequest req) {
        Member parent = parent(personaId);
        if (req.isEmpty()) throw new GeneralException(ErrorStatus.INVALID_INPUT, "바꿀 내용을 보내 주세요.");
        if (req.title() != null && req.title().isBlank()) throw new GeneralException(ErrorStatus.INVALID_INPUT, "미션 이름을 입력해 주세요.");

        Mission mission = familyMissionForUpdate(parent, missionId);
        if (mission.isNotOpenedYet()) throw new GeneralException(ErrorStatus.MISSION_NOT_EDITABLE, NOT_OPENED_YET);
        if (!mission.canEdit()) throw new GeneralException(ErrorStatus.MISSION_NOT_EDITABLE);
        if (req.rewardAmount() != null && mission.getStatus() == Mission.Status.SUBMITTED) {
            throw new GeneralException(ErrorStatus.MISSION_NOT_EDITABLE, "아이가 낸 완료를 먼저 확인한 뒤 보상을 바꿔 주세요.");
        }
        if (req.endDate() != null && req.endDate().isBefore(seedRepository.today())) {
            throw new GeneralException(ErrorStatus.INVALID_PERIOD, "마감일은 오늘 이후로 정해 주세요.");
        }
        if (req.endDate() != null && mission.getStartDate() != null && req.endDate().isBefore(mission.getStartDate())) {
            throw new GeneralException(ErrorStatus.INVALID_PERIOD);
        }
        if (req.rewardAmount() != null) checkRewardCap(parent, childOf(parent), req.rewardAmount());

        mission.edit(req.title() == null ? null : req.title().strip(), req.criteria(), req.rewardAmount(), req.endDate());
        if (mission.getStatus() == Mission.Status.IN_PROGRESS) {
            mission.afterApproval((int) submissionRepository.countByMission_MissionIdAndReviewStatus(
                    mission.getMissionId(), MissionSubmission.ReviewStatus.APPROVED));
        }
        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    /** 미션 취소 (소프트 삭제 → CANCELED). 확인 대기 중이면 제출을 먼저 처리해야 하고, 끝난 미션은 409 */
    @Transactional
    public MissionCreateResponse cancelMission(Long personaId, Long missionId) {
        Member parent = parent(personaId);
        Mission mission = familyMissionForUpdate(parent, missionId);
        if (mission.isNotOpenedYet()) throw new GeneralException(ErrorStatus.MISSION_NOT_CANCELABLE, NOT_OPENED_YET);
        if (mission.getStatus() == Mission.Status.SUBMITTED) {
            throw new GeneralException(ErrorStatus.MISSION_NOT_CANCELABLE, "아이가 낸 완료를 먼저 확인해 주세요.");
        }
        if (!mission.canCancel()) throw new GeneralException(ErrorStatus.MISSION_NOT_CANCELABLE);
        mission.cancel();
        return new MissionCreateResponse(mission.getMissionId(), mission.getStatus());
    }

    private Optional<Member> childOf(Member parent) {
        return memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD);
    }

    private void checkRewardCap(Member parent, Optional<Member> child, int reward) {
        long remaining = remainingRewardCap(parent.getFamily().getFamilyId(), child);
        if (reward > remaining) throw capExceeded(remaining);
    }

    private void notifyNewMission(Optional<Member> child, Mission mission) {
        child.ifPresent(c -> notificationRepository.save(Notification.of(
                c, Notification.Type.NEW_MISSION, "새 미션이 열렸어요", mission.getTitle(),
                mission.getMissionId(), demoNow())));
    }

    /**
     * 미션 보상 남은 한도 = 이번 정산 기간 용돈 × 미션 상한 비율 − 현재 정산 기간에 받은 미션 보상.
     * 명세대로 지급된 보상만 뺀다 (진행 중인 미션은 빼지 않음). 실제 초과 지급은 승인(지급) 시점에 막는다.
     * 용돈 설정이 없으면 한도 없음, 진행 중인 정산이 없으면 받은 보상 0.
     */
    private long remainingRewardCap(Long familyId, Optional<Member> child) {
        return rewardCap(familyId, child).map(RewardCap::remaining).orElse(Long.MAX_VALUE);
    }

    /** 이번 정산 기간 보상 한도와 받은 보상. 정산 기간 끝나는 달의 용돈 기준이고, 데모 기준일 화면 숫자와 같다 */
    record RewardCap(YearMonth month, int rate, long cap, long received) {
        long remaining() {
            return Math.max(cap - received, 0);
        }
    }

    /** 용돈 설정이나 용돈이 없으면 한도 없음(empty) */
    private Optional<RewardCap> rewardCap(Long familyId, Optional<Member> child) {
        FamilySetting setting = familySettingRepository.findById(familyId).orElse(null);
        if (setting == null) return Optional.empty();

        LocalDate today = seedRepository.today();
        Optional<Settlement> current = settlementRepository.findCurrent(familyId, today);
        // 용돈은 정산 기간 끝나는 달에 들어온다 (9/26~10/25 기간 → 10월 용돈). 10/26~10/31이면 11월 용돈 기준
        YearMonth month = YearMonth.from(current.map(Settlement::getPeriodEnd).orElse(today));
        Integer allowance = allowanceChangeRepository.findForMonth(familyId, month, setting.getAllowanceDay())
                .map(AllowanceChange::getAmount)
                .orElse(null);
        if (allowance == null) return Optional.empty();

        long cap = (long) allowance * setting.getMissionCapRate() / 100;
        long received = current.isEmpty() ? 0L : child.flatMap(c -> accountRepository.findByMemberId(c.getMemberId()))
                .map(account -> transferRepository.sumReceived(account.getAccountId(), Transfer.Type.MISSION_REWARD,
                        current.get().getPeriodStart().atStartOfDay(),
                        current.get().getPeriodEnd().plusDays(1).atStartOfDay()))
                .orElse(0L);
        return Optional.of(new RewardCap(month, setting.getMissionCapRate(), cap, received));
    }

    @Transactional
    public MissionSubmitResponse submitMission(Long personaId, Long missionId, List<MultipartFile> photos) {
        Member child = child(personaId);
        Mission mission = familyMissionForUpdate(child, missionId);
        if (!mission.canSubmit(seedRepository.today())) throw new GeneralException(ErrorStatus.MISSION_NOT_SUBMITTABLE);

        List<MultipartFile> files = photos == null ? List.of() : photos.stream().filter(f -> !f.isEmpty()).toList();
        if (mission.getVerifyMethod() == Mission.VerifyMethod.PHOTO && files.isEmpty()) {
            throw new GeneralException(ErrorStatus.PHOTO_REQUIRED);
        }
        files.forEach(photoStorage::validate);

        MissionSubmission submission = submissionRepository.save(MissionSubmission.of(mission, demoNow()));
        files.forEach(f -> photoRepository.save(SubmissionPhoto.of(submission, photoStorage.save(f))));
        mission.submit();

        return new MissionSubmitResponse(submission.getSubmissionId(), mission.getStatus());
    }

    /**
     * 승인: 보상을 부모 → 자녀 계좌로 보내고(이체 기록 + 자녀 잔액 증가) 알림을 보낸다. 한 트랜잭션이라 중간에 실패하면 전부 되돌린다.
     * 실제로 돈이 나가는 곳이라 한도를 지급할 때마다 다시 검사한다 (반복 미션·여러 달에 걸친 미션도 회차마다 걸림).
     */
    @Transactional
    public SubmissionReviewDtos.ApproveResponse approve(Long personaId, Long submissionId) {
        Member parent = parent(personaId);
        MissionSubmission submission = familySubmission(parent, submissionId);
        if (!submission.isPending()) throw new GeneralException(ErrorStatus.ALREADY_REVIEWED);

        Mission mission = submission.getMission();
        int reward = mission.getRewardAmount() == null ? 0 : mission.getRewardAmount();
        Optional<Member> child = childOf(parent);
        checkRewardCap(parent, child, reward);

        Account childAccount = child.flatMap(c -> accountRepository.findByMemberId(c.getMemberId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.ACCOUNT_NOT_FOUND));
        Long parentAccountId = accountRepository.findByMemberId(parent.getMemberId()).map(Account::getAccountId).orElse(null);

        LocalDateTime now = demoNow();
        int approvedCount = (int) submissionRepository.countByMission_MissionIdAndReviewStatus(
                mission.getMissionId(), MissionSubmission.ReviewStatus.APPROVED) + 1;
        submission.approve(now);
        mission.afterApproval(approvedCount);
        // 부모 계좌 잔액은 시드에 없어서(0원) 빼지 않는다. 자녀 쪽 이번 달 쓸 수 있는 돈만 늘린다
        childAccount.deposit(reward);
        Transfer transfer = transferRepository.save(
                Transfer.missionReward(parentAccountId, childAccount.getAccountId(), reward, mission.getMissionId(), now));
        notificationRepository.save(Notification.of(child.get(), Notification.Type.MISSION_REWARD, "보상이 들어왔어요",
                String.format(Locale.KOREA, "%s · %,d원", mission.getTitle(), reward), mission.getMissionId(), now));

        return new SubmissionReviewDtos.ApproveResponse(submission.getSubmissionId(), submission.getReviewStatus(),
                mission.getStatus(), transfer.getTransferId(), reward);
    }

    /** 반려: 보상은 보내지 않고 미션을 다시 진행 중으로 돌린 뒤, 사유와 함께 자녀에게 알린다 */
    @Transactional
    public SubmissionReviewDtos.RejectResponse reject(Long personaId, Long submissionId, MissionSubmission.RejectReason reason) {
        Member parent = parent(personaId);
        MissionSubmission submission = familySubmission(parent, submissionId);
        if (!submission.isPending()) throw new GeneralException(ErrorStatus.ALREADY_REVIEWED);

        Mission mission = submission.getMission();
        LocalDateTime now = demoNow();
        submission.reject(reason, now);
        mission.afterRejection();
        memberRepository.findByFamily_FamilyIdAndRole(parent.getFamily().getFamilyId(), Member.Role.CHILD)
                .ifPresent(c -> notificationRepository.save(Notification.of(c, Notification.Type.MISSION_REJECTED,
                        "미션을 다시 확인해 주세요", mission.getTitle() + " · " + reason.label, mission.getMissionId(), now)));

        return new SubmissionReviewDtos.RejectResponse(submission.getSubmissionId(), submission.getReviewStatus(),
                submission.getRejectReason(), mission.getStatus());
    }

    /**
     * 확인을 기다리는 제출을 부모에게 다시 알린다 (MISSION_REMIND, refId = submissionId). 이미 승인·반려됐으면 409.
     * 부모 알림함에 같은 알림이 쌓이지 않도록 제출 하나에 하루(데모 기준일) 한 번만 보낸다.
     * 연타하면 제출 행 잠금에서 줄을 서는데, 잠금이 풀린 뒤 앞 요청이 저장한 알림이 보여야 해서 READ_COMMITTED로 읽는다
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void remindSubmission(Long personaId, Long submissionId) {
        Member child = child(personaId);
        MissionSubmission submission = familySubmission(child, submissionId);
        if (!submission.isPending()) throw new GeneralException(ErrorStatus.ALREADY_REVIEWED, "이미 부모님이 확인한 미션이에요.");
        if (notificationRepository.existsByTypeAndRefIdAndCreatedAtGreaterThanEqual(
                Notification.Type.MISSION_REMIND, submissionId, seedRepository.today().atStartOfDay())) {
            throw new GeneralException(ErrorStatus.ALREADY_REMINDED);
        }

        memberRepository.findByFamily_FamilyIdAndRole(child.getFamily().getFamilyId(), Member.Role.PARENT)
                .ifPresent(p -> notificationRepository.save(Notification.of(p, Notification.Type.MISSION_REMIND,
                        child.getName() + (KoreanText.hasBatchim(child.getName()) ? "이" : "가") + " 미션 확인을 기다려요",
                        submission.getMission().getTitle(), submission.getSubmissionId(), demoNow())));
    }

    /**
     * 첫단추 추천 미션 목록. 자녀 화면(CHILD)과 부모 화면(PARENT) 추천은 따로이고, target은 내 역할과 같아야 한다.
     * 10개씩, cursor는 이전 페이지 마지막 missionId. 추천은 시드로 넣어 둔다
     */
    public MissionQueryDtos.RecommendationListResponse getRecommendations(Long personaId, Mission.RecommendTo recommendTo, Long cursor) {
        Member member = memberRepository.getPersona(personaId);
        if (recommendTo == null) throw new GeneralException(ErrorStatus.INVALID_INPUT, "target은 CHILD 또는 PARENT예요.");
        boolean mine = (member.getRole() == Member.Role.CHILD) == (recommendTo == Mission.RecommendTo.CHILD);
        if (!mine) throw new GeneralException(ErrorStatus.INVALID_INPUT, "내 화면의 추천만 볼 수 있어요.");

        List<Mission> page = missionRepository.findByFamily_FamilyIdAndStatusAndRecommendToAndMissionIdGreaterThanOrderByMissionId(
                member.getFamily().getFamilyId(), Mission.Status.RECOMMENDED, recommendTo, cursor == null ? 0L : cursor,
                PageRequest.of(0, RECOMMENDATION_PAGE_SIZE + 1));
        boolean hasNext = page.size() > RECOMMENDATION_PAGE_SIZE;
        List<MissionQueryDtos.Recommendation> items = page.stream().limit(RECOMMENDATION_PAGE_SIZE)
                .map(m -> new MissionQueryDtos.Recommendation(m.getMissionId(), m.getTitle(), m.getMissionType(), m.getRewardAmount(), null))
                .toList();
        return new MissionQueryDtos.RecommendationListResponse(items, hasNext ? items.get(items.size() - 1).missionId() : null);
    }

    private Member child(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.CHILD_ONLY);
        return member;
    }

    private Member parent(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        if (member.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        return member;
    }

    /** 내 가족 미션의 제출. 다른 가족 것이면 404 */
    private MissionSubmission familySubmission(Member member, Long submissionId) {
        return submissionRepository.findByIdForUpdate(submissionId)
                .filter(s -> s.getMission().getFamily().getFamilyId().equals(member.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.SUBMISSION_NOT_FOUND));
    }

    private static GeneralException capExceeded(long remaining) {
        return new GeneralException(ErrorStatus.REWARD_CAP_EXCEEDED,
                String.format(Locale.KOREA, "이번 달 남은 한도(%,d원)를 넘어요.", remaining));
    }

    /**
     * 데모 기준일 + 지금 시각. 이체·제출·알림 시각은 실제 날짜가 아니라 데모 기준일로 남긴다
     * (실제 날짜로 남기면 현재 정산 기간 밖이 돼서 받은 보상·한도 계산에서 빠진다).
     */
    private LocalDateTime demoNow() {
        return seedRepository.today().atTime(LocalTime.now().withNano(0));
    }

    /**
     * 제안 미션 목록 (PROPOSED·ON_HOLD). 자녀·부모 모두 보류한 제안까지 본다.
     * 부모가 "다음에"를 누른 제안도 나중에 다시 열 수 있어야 해서 부모 목록에서도 빼지 않는다 (status로 구분).
     */
    public MissionQueryDtos.ProposedListResponse getProposedMissions(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        List<MissionQueryDtos.ProposedMission> missions = missionRepository
                .findByFamily_FamilyIdAndStatusInOrderByCreatedAtDesc(member.getFamily().getFamilyId(), PROPOSALS)
                .stream()
                .map(m -> new MissionQueryDtos.ProposedMission(m.getMissionId(), m.getTitle(), m.getMissionType(),
                        m.getStatus(), m.getCriteria(), m.getDesiredReward(), m.getCreatedAt()))
                .toList();
        return new MissionQueryDtos.ProposedListResponse(missions.size(), missions);
    }

    /** 종료 미션 목록 (완료·취소·만료), 최신순. cursor는 이전 페이지 마지막 missionId */
    public MissionQueryDtos.EndedListResponse getEndedMissions(Long personaId, Long cursor) {
        Member member = memberRepository.getPersona(personaId);
        List<Mission> page = missionRepository.findByFamily_FamilyIdAndStatusInAndMissionIdLessThanOrderByMissionIdDesc(
                member.getFamily().getFamilyId(), ENDED, cursor == null ? Long.MAX_VALUE : cursor,
                PageRequest.of(0, ENDED_PAGE_SIZE + 1));
        boolean hasNext = page.size() > ENDED_PAGE_SIZE;
        List<MissionQueryDtos.EndedMission> missions = page.stream().limit(ENDED_PAGE_SIZE)
                .map(m -> new MissionQueryDtos.EndedMission(m.getMissionId(), m.getTitle(), m.getStatus(), m.getRewardAmount(),
                        transferRepository.sumByMission(m.getMissionId(), Transfer.Type.MISSION_REWARD), endedAt(m)))
                .toList();
        return new MissionQueryDtos.EndedListResponse(missions, hasNext ? missions.get(missions.size() - 1).missionId() : null);
    }

    /** 끝난 날: 완료된 미션은 마지막 승인일, 그 외(취소·만료)는 마감일 */
    private LocalDate endedAt(Mission m) {
        return submissionRepository
                .findFirstByMission_MissionIdAndReviewStatusOrderByReviewedAtDesc(m.getMissionId(), MissionSubmission.ReviewStatus.APPROVED)
                .filter(s -> m.getStatus() == Mission.Status.COMPLETED && s.getReviewedAt() != null)
                .map(s -> s.getReviewedAt().toLocalDate())
                .orElse(m.getEndDate());
    }

    /** 부모 확인을 기다리는 제출 목록. 자녀의 "확인 기다리는 미션"도 같은 API를 쓴다 */
    public MissionQueryDtos.PendingListResponse getPendingSubmissions(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        List<MissionSubmission> submissions = submissionRepository.findByMission_Family_FamilyIdAndReviewStatusOrderBySubmittedAtDesc(
                member.getFamily().getFamilyId(), MissionSubmission.ReviewStatus.PENDING);
        Map<Long, List<String>> photos = photosBySubmission(submissions);
        List<MissionQueryDtos.PendingSubmission> items = submissions.stream()
                .map(s -> new MissionQueryDtos.PendingSubmission(s.getSubmissionId(), s.getMission().getMissionId(),
                        s.getMission().getTitle(), s.getMission().getRewardAmount(),
                        photos.getOrDefault(s.getSubmissionId(), List.of()), s.getSubmittedAt()))
                .toList();
        return new MissionQueryDtos.PendingListResponse(items.size(), items);
    }

    /** 이번 달 보낸 보상과 한 달 상한. 미션 생성·승인의 한도 계산과 같은 값이라 자녀·부모 화면 숫자가 같다 */
    public MissionQueryDtos.RewardSummaryResponse getRewardSummary(Long personaId) {
        Member member = memberRepository.getPersona(personaId);
        Long familyId = member.getFamily().getFamilyId();
        Optional<Member> child = memberRepository.findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD);
        return rewardCap(familyId, child)
                .map(c -> new MissionQueryDtos.RewardSummaryResponse(c.month().toString(), c.received(), c.cap(), c.rate(), c.remaining()))
                .orElseGet(() -> new MissionQueryDtos.RewardSummaryResponse(YearMonth.from(seedRepository.today()).toString(), 0, null, null, null));
    }

    private Map<Long, List<String>> photosBySubmission(List<MissionSubmission> submissions) {
        if (submissions.isEmpty()) return Map.of();
        return photoRepository
                .findBySubmission_SubmissionIdInOrderByPhotoId(submissions.stream().map(MissionSubmission::getSubmissionId).toList())
                .stream()
                .collect(Collectors.groupingBy(p -> p.getSubmission().getSubmissionId(),
                        Collectors.mapping(SubmissionPhoto::getImageUrl, Collectors.toList())));
    }

    public MissionDetailResponse getMission(Long personaId, Long missionId) {
        Member member = memberRepository.getPersona(personaId);
        Mission m = familyMission(member, missionId);

        List<MissionSubmission> submissions = submissionRepository.findByMission_MissionIdOrderBySubmittedAtDesc(missionId);
        Map<Long, List<String>> photos = photosBySubmission(submissions);

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

    /** 상태를 바꿀 때 쓰는 잠근 조회 (열기·보류·제출). 동시에 온 같은 요청은 한 줄로 처리된다 */
    private Mission familyMissionForUpdate(Member member, Long missionId) {
        return missionRepository.findByIdForUpdate(missionId)
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
