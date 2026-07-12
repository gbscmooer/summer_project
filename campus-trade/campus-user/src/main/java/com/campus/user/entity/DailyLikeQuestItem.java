package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_daily_like_quest_item")
public class DailyLikeQuestItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate questDate;
    private Long postId;
    private LocalDateTime createTime;
}
