-- 서버 실행 시마다 테이블을 지우고 다시 만듦 (spring.sql.init.mode=always)
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS families, members, accounts, family_settings, allowance_changes, allowance_suggestions, price_indexes, settlements, card_payments, missions, transfers, mission_submissions, submission_photos, chat_messages, briefings, child_messages, notifications, notification_settings, certificates, consents, rag_documents;
SET FOREIGN_KEY_CHECKS = 1;

-- 첫단추 ERD (A·B 전체) - MySQL
-- 기준: 첫단추 기능 명세서 / 첫단추 ERD.md (2026-10-29)
-- COMMENT = ERDCloud 논리 이름. 상세 설명은 첫단추 ERD.md 참고

CREATE TABLE families (
  family_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '가족 아이디',
  name      VARCHAR(20) NOT NULL COMMENT '가족 이름',
  PRIMARY KEY (family_id)
) COMMENT '가족';

CREATE TABLE members (
  member_id  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '구성원 아이디',
  family_id  BIGINT      NOT NULL COMMENT '가족 아이디',
  role       ENUM('PARENT','CHILD') NOT NULL COMMENT '역할',
  name       VARCHAR(20) NOT NULL COMMENT '이름',
  birth_date DATE        NOT NULL COMMENT '생년월일',
  relation   VARCHAR(50) NULL     COMMENT '관계 표시',
  PRIMARY KEY (member_id),
  UNIQUE KEY uk_members_family_role (family_id, role),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '구성원';

CREATE TABLE accounts (
  account_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '계좌 아이디',
  member_id      BIGINT      NOT NULL COMMENT '구성원 아이디',
  bank_name      VARCHAR(20) NOT NULL COMMENT '은행명',
  account_number VARCHAR(30) NOT NULL COMMENT '계좌번호',
  balance        INT         NOT NULL DEFAULT 0 COMMENT '잔액',
  PRIMARY KEY (account_id),
  UNIQUE KEY uk_accounts_member (member_id),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '연결 계좌';

CREATE TABLE family_settings (
  family_id        BIGINT  NOT NULL COMMENT '가족 아이디',
  allowance_day    TINYINT NOT NULL DEFAULT 1 COMMENT '용돈 보내는 날',
  auto_transfer    BOOLEAN NOT NULL DEFAULT TRUE COMMENT '자동이체 여부',
  settlement_day   TINYINT NOT NULL DEFAULT 25 COMMENT '결제일',
  visibility       ENUM('TOTAL_ONLY') NOT NULL DEFAULT 'TOTAL_ONLY' COMMENT '부모 열람 범위',
  mission_cap_rate TINYINT NOT NULL DEFAULT 30 COMMENT '미션 상한 비율',
  card_limit       INT     NULL     COMMENT '카드 한도',
  PRIMARY KEY (family_id),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '가족 약속 설정';

CREATE TABLE allowance_changes (
  change_id      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '변경 아이디',
  family_id      BIGINT   NOT NULL COMMENT '가족 아이디',
  prev_amount    INT      NULL     COMMENT '이전 금액',
  amount         INT      NOT NULL COMMENT '변경 금액',
  effective_from DATE     NOT NULL COMMENT '적용 시작일',
  reason         ENUM('INFLATION','SCHOOL','RENEGOTIATION','ETC','INITIAL') NULL COMMENT '바꾸는 이유',
  source         ENUM('PARENT','AI_SUGGESTION') NOT NULL COMMENT '출처',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경일',
  PRIMARY KEY (change_id),
  KEY idx_allowance_changes_family (family_id, effective_from),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '용돈 변경 이력';

CREATE TABLE allowance_suggestions (
  suggestion_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '제안 아이디',
  family_id        BIGINT       NOT NULL COMMENT '가족 아이디',
  current_amount   INT          NOT NULL COMMENT '현재 용돈',
  suggested_amount INT          NOT NULL COMMENT '제안 금액',
  range_min        INT          NOT NULL COMMENT '참고 구간 최소',
  range_max        INT          NOT NULL COMMENT '참고 구간 최대',
  inflation_rate   DECIMAL(4,1) NOT NULL COMMENT '물가 상승률',
  basis            JSON         NULL     COMMENT '근거',
  explanation      TEXT         NULL     COMMENT '설명 문장',
  status           ENUM('PENDING','APPLIED','KEPT') NOT NULL DEFAULT 'PENDING' COMMENT '상태',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제안일',
  PRIMARY KEY (suggestion_id),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT 'AI 용돈 제안';

CREATE TABLE price_indexes (
  price_index_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '물가 아이디',
  category       VARCHAR(20)  NOT NULL COMMENT '결제 카테고리',
  base_month     CHAR(7)      NOT NULL COMMENT '기준월',
  change_rate    DECIMAL(4,1) NOT NULL COMMENT '전년 대비 상승률',
  source         VARCHAR(100) NULL     COMMENT '출처',
  PRIMARY KEY (price_index_id),
  UNIQUE KEY uk_price_indexes_category_month (category, base_month)
) COMMENT '품목별 물가';

CREATE TABLE settlements (
  settlement_id  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '정산 아이디',
  family_id      BIGINT   NOT NULL COMMENT '가족 아이디',
  period_start   DATE     NOT NULL COMMENT '기간 시작',
  period_end     DATE     NOT NULL COMMENT '기간 끝',
  due_at         DATETIME NOT NULL COMMENT '정산 시각',
  billed_amount  INT      NULL     COMMENT '청구액',
  balance_at_due INT      NULL     COMMENT '결제일 시점 잔액',
  paid_amount    INT      NOT NULL DEFAULT 0 COMMENT '이체된 금액',
  status         ENUM('SCHEDULED','REQUIRED','SHORTFALL','PARTIAL','COMPLETED') NOT NULL DEFAULT 'SCHEDULED' COMMENT '정산 상태',
  result         ENUM('FULL_PAID','SELF_RESOLVED','AUTO_DEDUCTED') NULL COMMENT '월별 결과',
  is_excluded    BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '리포트 제외 여부',
  exclude_reason ENUM('PARENT_SUPPORT','CASH') NULL COMMENT '제외 사유',
  resolved_at    DATETIME NULL     COMMENT '완료 일시',
  PRIMARY KEY (settlement_id),
  UNIQUE KEY uk_settlements_family_period (family_id, period_end),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '월별 정산';

CREATE TABLE card_payments (
  payment_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '결제 아이디',
  member_id     BIGINT      NOT NULL COMMENT '구성원 아이디',
  settlement_id BIGINT      NOT NULL COMMENT '정산 아이디',
  merchant_name VARCHAR(50) NOT NULL COMMENT '가맹점명',
  category      VARCHAR(20) NOT NULL COMMENT '카테고리',
  amount        INT         NOT NULL COMMENT '금액',
  is_canceled   BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '취소 여부',
  risk_type     ENUM('GAMBLING','HIGH_AMOUNT','PROXY_DEPOSIT') NULL COMMENT '위험 결제 유형',
  paid_at       DATETIME    NOT NULL COMMENT '결제 일시',
  PRIMARY KEY (payment_id),
  KEY idx_card_payments_member_paid (member_id, paid_at),
  FOREIGN KEY (member_id) REFERENCES members (member_id),
  FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id)
) COMMENT '카드 결제';

CREATE TABLE missions (
  mission_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '미션 아이디',
  family_id      BIGINT       NOT NULL COMMENT '가족 아이디',
  title          VARCHAR(50)  NOT NULL COMMENT '미션 이름',
  criteria       VARCHAR(200) NULL     COMMENT '완료 기준',
  mission_type   ENUM('CHORE','FINANCE','HABIT') NULL COMMENT '유형',
  reward_amount  INT          NULL     COMMENT '보상 금액',
  desired_reward INT          NULL     COMMENT '바라는 보상',
  start_date     DATE         NULL     COMMENT '시작일',
  end_date       DATE         NULL     COMMENT '마감일',
  repeat_type    ENUM('ONCE','DAILY','WEEKLY','MONTHLY') NOT NULL DEFAULT 'ONCE' COMMENT '반복',
  verify_method  ENUM('PHOTO','DIRECT') NULL COMMENT '확인 방법',
  origin         ENUM('PARENT','CHILD','AI') NOT NULL COMMENT '출처',
  recommend_to   ENUM('CHILD','PARENT') NULL COMMENT '추천 대상',
  status         ENUM('RECOMMENDED','PROPOSED','ON_HOLD','NEW','IN_PROGRESS','SUBMITTED','COMPLETED','CANCELED','EXPIRED') NOT NULL COMMENT '상태',
  progress       TINYINT      NOT NULL DEFAULT 0 COMMENT '진행률',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  PRIMARY KEY (mission_id),
  KEY idx_missions_family_status (family_id, status),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '미션';

CREATE TABLE transfers (
  transfer_id     BIGINT   NOT NULL AUTO_INCREMENT COMMENT '이체 아이디',
  from_account_id BIGINT   NOT NULL COMMENT '보낸 계좌',
  to_account_id   BIGINT   NOT NULL COMMENT '받은 계좌',
  type            ENUM('ALLOWANCE','SETTLEMENT','SHORTFALL_PAYMENT','AUTO_DEDUCT','MISSION_REWARD','PARENT_SUPPORT') NOT NULL COMMENT '이체 종류',
  amount          INT      NOT NULL COMMENT '금액',
  settlement_id   BIGINT   NULL     COMMENT '정산 아이디',
  mission_id      BIGINT   NULL     COMMENT '미션 아이디',
  transferred_at  DATETIME NOT NULL COMMENT '이체 일시',
  PRIMARY KEY (transfer_id),
  FOREIGN KEY (from_account_id) REFERENCES accounts (account_id),
  FOREIGN KEY (to_account_id) REFERENCES accounts (account_id),
  FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id),
  FOREIGN KEY (mission_id) REFERENCES missions (mission_id)
) COMMENT '이체 내역';

CREATE TABLE mission_submissions (
  submission_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '제출 아이디',
  mission_id    BIGINT   NOT NULL COMMENT '미션 아이디',
  review_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '확인 결과',
  reject_reason  ENUM('PHOTO_UNCLEAR','NOT_DONE') NULL COMMENT '반려 사유',
  submitted_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제출 일시',
  reviewed_at   DATETIME NULL     COMMENT '확인 일시',
  PRIMARY KEY (submission_id),
  FOREIGN KEY (mission_id) REFERENCES missions (mission_id) ON DELETE CASCADE
) COMMENT '미션 완료 제출';

CREATE TABLE submission_photos (
  photo_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사진 아이디',
  submission_id BIGINT       NOT NULL COMMENT '제출 아이디',
  image_url     VARCHAR(500) NOT NULL COMMENT '이미지 경로',
  PRIMARY KEY (photo_id),
  FOREIGN KEY (submission_id) REFERENCES mission_submissions (submission_id) ON DELETE CASCADE
) COMMENT '제출 사진';

CREATE TABLE chat_messages (
  message_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '메시지 아이디',
  member_id  BIGINT   NOT NULL COMMENT '구성원 아이디',
  sender     ENUM('CHILD','BOT') NOT NULL COMMENT '보낸 쪽',
  content    TEXT     NOT NULL COMMENT '내용',
  card_type  ENUM('TEXT','NUMBER','RAG','RETROSPECT') NOT NULL DEFAULT 'TEXT' COMMENT '카드 종류',
  payload    JSON     NULL     COMMENT '카드 데이터',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
  PRIMARY KEY (message_id),
  KEY idx_chat_messages_member (member_id, created_at),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '대화 메시지';

CREATE TABLE briefings (
  briefing_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '브리핑 아이디',
  family_id     BIGINT       NOT NULL COMMENT '가족 아이디',
  target_month  CHAR(7)      NOT NULL COMMENT '대상 월',
  spent_line    VARCHAR(200) NULL     COMMENT '사용',
  saved_line    VARCHAR(200) NULL     COMMENT '남긴 돈',
  changed_line  VARCHAR(200) NULL     COMMENT '달라진 점',
  good_line     VARCHAR(200) NULL     COMMENT '잘한 점',
  together_line VARCHAR(200) NULL     COMMENT '같이 볼 점',
  read_at       DATETIME     NULL     COMMENT '읽은 일시',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  PRIMARY KEY (briefing_id),
  UNIQUE KEY uk_briefings_family_month (family_id, target_month),
  FOREIGN KEY (family_id) REFERENCES families (family_id)
) COMMENT '월간 브리핑';

CREATE TABLE child_messages (
  child_message_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '전할 말 아이디',
  family_id        BIGINT       NOT NULL COMMENT '가족 아이디',
  message_id       BIGINT       NULL     COMMENT '출처 메시지',
  briefing_id      BIGINT       NULL     COMMENT '포함된 브리핑',
  request          VARCHAR(200) NOT NULL COMMENT '요청',
  reason           VARCHAR(200) NULL     COMMENT '이유',
  status           ENUM('CANDIDATE','MONTHLY','SENT','DISCUSS','ACKNOWLEDGED','DISCARDED') NOT NULL DEFAULT 'CANDIDATE' COMMENT '상태',
  delivery         ENUM('NOW','MONTHLY') NULL COMMENT '전한 방식',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  sent_at          DATETIME     NULL     COMMENT '부모에게 간 일시',
  decided_at       DATETIME     NULL     COMMENT '부모 처리 일시',
  PRIMARY KEY (child_message_id),
  FOREIGN KEY (family_id) REFERENCES families (family_id),
  FOREIGN KEY (message_id) REFERENCES chat_messages (message_id) ON DELETE SET NULL,
  FOREIGN KEY (briefing_id) REFERENCES briefings (briefing_id)
) COMMENT '아이가 전하는 말';

CREATE TABLE notifications (
  notification_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 아이디',
  member_id       BIGINT       NOT NULL COMMENT '받는 사람',
  type            ENUM('SETTLEMENT_RESULT','SHORTFALL_EXPECTED','SHORTFALL_RESOLVED','AUTO_DEDUCT','RISK_PAYMENT','ADULT_TRANSITION','ALLOWANCE_CHANGED','NEW_MISSION','MISSION_REJECTED','MISSION_REWARD','MISSION_PROPOSED','MISSION_REMIND') NOT NULL COMMENT '알림 종류',
  title           VARCHAR(50)  NOT NULL COMMENT '제목',
  body            VARCHAR(200) NULL     COMMENT '내용',
  ref_id          BIGINT       NULL     COMMENT '관련 아이디',
  read_at         DATETIME     NULL     COMMENT '읽은 일시',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  PRIMARY KEY (notification_id),
  KEY idx_notifications_member (member_id, created_at),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '알림';

CREATE TABLE notification_settings (
  member_id BIGINT  NOT NULL COMMENT '구성원 아이디',
  type      ENUM('SETTLEMENT_RESULT','SHORTFALL_EXPECTED','SHORTFALL_RESOLVED','AUTO_DEDUCT','RISK_PAYMENT','ADULT_TRANSITION','ALLOWANCE_CHANGED','NEW_MISSION','MISSION_REJECTED','MISSION_REWARD','MISSION_PROPOSED','MISSION_REMIND') NOT NULL COMMENT '알림 종류',
  enabled   BOOLEAN NOT NULL COMMENT '켜짐 여부',
  PRIMARY KEY (member_id, type),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '알림 설정';

CREATE TABLE certificates (
  certificate_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '확인서 아이디',
  member_id           BIGINT       NOT NULL COMMENT '구성원 아이디',
  full_paid_months    TINYINT      NOT NULL COMMENT '결제일 완납 개월',
  total_months        TINYINT      NOT NULL COMMENT '대상 개월',
  max_streak          TINYINT      NOT NULL COMMENT '최장 연속 완납',
  avg_margin_rate     DECIMAL(4,1) NOT NULL COMMENT '결제일 여유율 평균',
  self_resolved_count TINYINT      NOT NULL COMMENT '부족분 스스로 해소',
  shortfall_count     TINYINT      NOT NULL COMMENT '부족 발생',
  file_path           VARCHAR(500) NOT NULL COMMENT 'PDF 경로',
  share_token         VARCHAR(64)  NULL     COMMENT '공유 링크 토큰',
  share_expires_at    DATETIME     NULL     COMMENT '공유 링크 만료',
  issued_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급일',
  expires_at          DATETIME     NOT NULL COMMENT '유효기한',
  PRIMARY KEY (certificate_id),
  UNIQUE KEY uk_certificates_share_token (share_token),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '금융행동 기록 확인서';

CREATE TABLE consents (
  consent_id   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '동의 아이디',
  member_id    BIGINT      NOT NULL COMMENT '구성원 아이디',
  consent_type VARCHAR(50) NOT NULL COMMENT '동의 항목',
  agreed       BOOLEAN     NOT NULL COMMENT '동의 여부',
  agreed_at    DATETIME    NULL     COMMENT '동의 일시',
  PRIMARY KEY (consent_id),
  FOREIGN KEY (member_id) REFERENCES members (member_id)
) COMMENT '개인정보 동의';

CREATE TABLE rag_documents (
  doc_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '자료 아이디',
  title   VARCHAR(100) NOT NULL COMMENT '용어',
  content TEXT         NOT NULL COMMENT '내용',
  source  VARCHAR(100) NOT NULL COMMENT '출처',
  PRIMARY KEY (doc_id)
) COMMENT '금융 설명 자료';
