-- Optional upgrade: topic post tip_total for points tipping.
USE campus_trade;

ALTER TABLE t_topic_post
    ADD COLUMN tip_total INT NOT NULL DEFAULT 0 COMMENT '累计打赏积分' AFTER comment_count;
