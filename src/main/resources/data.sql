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

-- 4. 미션 제출 (분리수거 4회 승인 → 4/5 = 80%, 영수증 1건 확인 대기)
INSERT INTO mission_submissions (mission_id, review_status, submitted_at, reviewed_at) VALUES
(1, 'APPROVED', '2026-09-27 19:00:00', '2026-09-27 21:00:00'),
(1, 'APPROVED', '2026-10-04 19:00:00', '2026-10-04 21:00:00'),
(1, 'APPROVED', '2026-10-11 19:00:00', '2026-10-11 21:00:00'),
(1, 'APPROVED', '2026-10-18 19:00:00', '2026-10-18 21:00:00'),
(2, 'PENDING',  '2026-10-18 20:30:00', NULL);

