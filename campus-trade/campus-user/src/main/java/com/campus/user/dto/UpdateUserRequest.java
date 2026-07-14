package com.campus.user.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String nickname;
    /** 头像：仅允许站内上传路径 /api/product/image/{32hex}.{jpg|png|webp}；传空字符串可清空 */
    private String avatar;
    private String phone;
    /** 个性签名，传空字符串可清空 */
    private String bio;
    /** 主页封面图：仅允许站内上传路径；传空字符串可清空 */
    private String coverImage;
    /** 展示用 IP 属地，传空字符串可清空 */
    private String ipLocation;
}
