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