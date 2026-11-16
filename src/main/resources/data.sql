-- 1. 가족
INSERT INTO families (family_id, name) VALUES (1, '가족 A');
INSERT INTO families (family_id, name) VALUES (2, '가족 B');

-- 2. 구성원
INSERT INTO members (member_id, family_id, role, name, birth_date, relation)
VALUES (1, 1, 'PARENT', '루피 엄마', '1978-05-10', '엄마');

INSERT INTO members (member_id, family_id, role, name, birth_date, relation)
VALUES (2, 1, 'CHILD', '루피', '2007-11-02', NULL);

INSERT INTO members (member_id, family_id, role, name, birth_date, relation)
VALUES (3, 2, 'PARENT', '뽀로로 엄마', '1976-08-21', '엄마');

INSERT INTO members (member_id, family_id, role, name, birth_date, relation)
VALUES (4, 2, 'CHILD', '뽀로로', '2007-10-03', NULL);
-- 3. 미션 (가족 A만. 가족 B는 진행 중 미션 없음)
INSERT INTO missions (mission_id, family_id, title, criteria, mission_type, reward_amount, start_date, end_date, repeat_type, verify_method, origin, status)
VALUES (1, 1, '분리수거 한 달 맡기', '매주 일요일 분리수거 후 사진 인증', 'CHORE', 3000, '2026-10-01', '2026-10-31', 'WEEKLY', 'PHOTO', 'PARENT', 'IN_PROGRESS');

INSERT INTO missions (mission_id, family_id, title, criteria, mission_type, reward_amount, start_date, end_date, repeat_type, verify_method, origin, status)
VALUES (2, 1, '이번 주 영수증 정리하기', '이번 주 영수증을 날짜별로 정리해서 사진 인증', 'FINANCE', 3000, '2026-10-12', '2026-10-18', 'ONCE', 'PHOTO', 'PARENT', 'SUBMITTED');

INSERT INTO missions (mission_id, family_id, title, criteria, mission_type, reward_amount, start_date, end_date, repeat_type, verify_method, origin, status)
VALUES (3, 1, '설거지 일주일', '매일 저녁 설거지 후 사진 인증', 'CHORE', 3000, '2026-10-19', '2026-10-25', 'DAILY', 'PHOTO', 'PARENT', 'NEW');

INSERT INTO missions (mission_id, family_id, title, criteria, mission_type, desired_reward, repeat_type, verify_method, origin, status, created_at)
VALUES (4, 1, '베란다 화분 물 주기 · 한 달', '일주일에 한 번 베란다 화분에 물 주기', 'CHORE', 3000, 'WEEKLY', 'PHOTO', 'CHILD', 'PROPOSED', '2026-10-17 20:00:00');

-- 첫단추 추천 미션 (자녀 화면·부모 화면 추천은 따로)
INSERT INTO missions (mission_id, family_id, title, criteria, mission_type, reward_amount, repeat_type, verify_method, origin, recommend_to, status) VALUES
(5,  1, '가격 비교 장보기', '같은 물건을 두 곳 이상에서 가격 비교하고 사진 인증', 'FINANCE', 3500, 'ONCE', 'PHOTO', 'AI', 'CHILD', 'RECOMMENDED'),
(6,  1, '일주일 결제 돌아보기', '일주일 결제 내역을 보고 아낄 수 있었던 곳 한 가지 적기', 'HABIT', 2500, 'ONCE', 'PHOTO', 'AI', 'CHILD', 'RECOMMENDED'),
(7,  1, '용돈 기입장 3일 쓰기', '3일 동안 쓴 돈을 기입장에 적고 사진 인증', 'HABIT', 2000, 'ONCE', 'PHOTO', 'AI', 'CHILD', 'RECOMMENDED'),
(8,  1, '냉장고 정리 돕기', '냉장고 칸별로 정리하고 유통기한 지난 것 골라내기', 'CHORE', 3000, 'ONCE', 'PHOTO', 'AI', 'PARENT', 'RECOMMENDED'),
(9,  1, '가족 장보기 예산 짜기', '이번 주 장보기 목록과 예산을 정하고 영수증과 비교하기', 'FINANCE', 3500, 'ONCE', 'PHOTO', 'AI', 'PARENT', 'RECOMMENDED'),
(10, 1, '하루 지출 0원 도전', '하루 동안 돈을 쓰지 않고 결제 내역으로 확인', 'HABIT', 2000, 'ONCE', 'DIRECT', 'AI', 'PARENT', 'RECOMMENDED');

-- 4. 미션 제출 (분리수거 4회 승인 → 4/5 = 80%, 영수증 1건 확인 대기)
INSERT INTO mission_submissions (mission_id, review_status, submitted_at, reviewed_at) VALUES
(1, 'APPROVED', '2026-09-27 19:00:00', '2026-09-27 21:00:00'),
(1, 'APPROVED', '2026-10-04 19:00:00', '2026-10-04 21:00:00'),
(1, 'APPROVED', '2026-10-11 19:00:00', '2026-10-11 21:00:00'),
(1, 'APPROVED', '2026-10-18 19:00:00', '2026-10-18 21:00:00'),
(2, 'PENDING',  '2026-10-18 20:30:00', NULL);


-- 5. 알림 (시연용. 테스트가 아이(2) 알림 개수를 세므로 부모 알림만 넣는다)
INSERT INTO notifications (member_id, type, title, body, ref_id, read_at, created_at) VALUES
(1, 'SETTLEMENT_RESULT', '9월 결제일 정산을 마쳤어요', '루피가 결제일에 모두 갚았어요.', NULL, '2026-09-25 20:00:00', '2026-09-25 18:00:00'),
(1, 'MISSION_REMIND', '루피가 미션 확인을 기다려요', '이번 주 영수증 정리하기', 5, NULL, '2026-10-18 21:30:00');


-- 6. 아이가 전하는 말 (지금 전한 말은 부모 결정 카드에, 월말에 전하기로 한 말은 월말 회고에 나온다)
INSERT INTO child_messages (family_id, message_id, request, reason, status, delivery, created_at, sent_at) VALUES
(1, NULL, '다음 달 친구 생일 선물비가 조금 더 필요해요', '미리 말해두고 싶어요', 'SENT', 'NOW', '2026-10-18 21:00:00', '2026-10-18 21:00:00'),
(1, NULL, '학원 가는 버스비가 생각보다 많이 나와요', '학원이 멀어져서 버스를 더 타요', 'MONTHLY', NULL, '2026-10-12 20:00:00', NULL);
