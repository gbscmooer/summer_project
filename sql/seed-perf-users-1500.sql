-- Expand load-test accounts to perfuser001..perfuser1500 (password: 123456).
-- Idempotent: only inserts missing usernames.
-- Usage:
--   docker exec -i campus-mysql mysql -ucampus -p"$MYSQL_PASSWORD" campus_trade < sql/seed-perf-users-1500.sql

USE campus_trade;

SET SESSION cte_max_recursion_depth = 2000;
SET @pwd = '$2a$10$fPwyITwOcv4Ggd3cbTdX6uxOQs5X96/CuKS/4K/3cz4gZhnt7nLJi';

-- 1..999（与历史 LPAD3 命名兼容：perfuser001）
INSERT INTO t_user (username, password, nickname, avatar, phone, role)
WITH RECURSIVE seq AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM seq WHERE i < 999
)
SELECT
    CONCAT('perfuser', LPAD(i, 3, '0')),
    @pwd,
    CONCAT('压测用户', i),
    CONCAT('https://api.dicebear.com/7.x/avataaars/svg?seed=perf', i),
    CONCAT('139', LPAD(10000000 + i, 8, '0')),
    0
FROM seq
WHERE NOT EXISTS (
    SELECT 1 FROM t_user u WHERE u.username = CONCAT('perfuser', LPAD(seq.i, 3, '0'))
);

-- 1000..1500（perfuser1000，无前导零）
INSERT INTO t_user (username, password, nickname, avatar, phone, role)
WITH RECURSIVE seq AS (
    SELECT 1000 AS i
    UNION ALL
    SELECT i + 1 FROM seq WHERE i < 1500
)
SELECT
    CONCAT('perfuser', i),
    @pwd,
    CONCAT('压测用户', i),
    CONCAT('https://api.dicebear.com/7.x/avataaars/svg?seed=perf', i),
    CONCAT('139', LPAD(10000000 + i, 8, '0')),
    0
FROM seq
WHERE NOT EXISTS (
    SELECT 1 FROM t_user u WHERE u.username = CONCAT('perfuser', seq.i)
);

SELECT
  COUNT(*) AS perf_users,
  MIN(username) AS first_user,
  MAX(username) AS last_user
FROM t_user
WHERE username LIKE 'perfuser%';
