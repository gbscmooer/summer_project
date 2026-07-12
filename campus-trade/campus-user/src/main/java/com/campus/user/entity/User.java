package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    /** 个性签名 */
    private String bio;
    private String avatar;
    /** 主页封面图 URL */
    private String coverImage;
    /** 展示用 IP 属地 */
    private String ipLocation;
    private String phone;
    private String email;
    /** 0-未验证 1-已验证（注册时不校验，预留） */
    private Integer emailVerified;
    /** 0-个人账户 1-管理员 2-商家 */
    private Integer role;
    /** 0-正常 1-已封禁 */
    private Integer status;
    /** 封禁原因 */
    private String banReason;
    /** 封禁截止时间，null 表示永久 */
    private LocalDateTime banUntil;
    /** 执行封禁的管理员 ID */
    private Long bannedBy;
    /** 封禁时间 */
    private LocalDateTime bannedAt;
    /** 1-已完成新手教程（老用户或已毕业） */
    private Integer onboardingCompleted;
    /** 新手教程步骤标记 JSON */
    private String onboardingFlags;
    /** 积分余额（新用户默认 100） */
    private Integer points;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
