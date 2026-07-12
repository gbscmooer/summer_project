-- 用户个性化主页：封面图、IP 属地
USE campus_trade;

SET NAMES utf8mb4;

ALTER TABLE t_user
    ADD COLUMN cover_image VARCHAR(500) NOT NULL DEFAULT '' COMMENT '主页封面图 URL' AFTER avatar,
    ADD COLUMN ip_location VARCHAR(50) NOT NULL DEFAULT '' COMMENT '展示用 IP 属地' AFTER bio;
