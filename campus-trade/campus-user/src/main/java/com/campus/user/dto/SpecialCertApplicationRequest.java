package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpecialCertApplicationRequest {
    @NotBlank(message = "请填写认证展示名称")
    @Size(max = 100, message = "认证展示名称最多100字")
    private String displayName;

    @NotBlank(message = "请填写申请说明")
    @Size(max = 500, message = "申请说明最多500字")
    private String reason;

    @NotBlank(message = "请填写联系电话")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String contactPhone;
}
