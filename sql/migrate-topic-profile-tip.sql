-- 用户个性签名 + 帖子打赏开关；校正 comment_count
-- 已有列时请跳过对应 ALTER

ALTER TABLE t_user
    ADD COLUMN bio VARCHAR(120) NOT NULL DEFAULT '' COMMENT '个性签名' AFTER nickname;

ALTER TABLE t_topic_post
    ADD COLUMN tip_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '0-关闭打赏 1-开启打赏' AFTER tip_total;

UPDATE t_topic_post p
SET comment_count = (
    SELECT COUNT(*) FROM t_topic_comment c WHERE c.post_id = p.id
);

UPDATE t_user SET bio = '校园集市老用户，常出教材数码' WHERE username = 'zhangsan' AND bio = '';
UPDATE t_user SET bio = '认证商家 · 好物不断货' WHERE username = 'lisi' AND bio = '';
UPDATE t_user SET bio = '爱逛跳蚤市场' WHERE username = 'wangwu' AND bio = '';
