package com.campus.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_config")
public class AiConfigEntity {
    @TableId
    private Long id;
    /** 1-使用本表覆盖环境变量默认值 */
    private Integer enabled;
    private String baseUrl;
    private String apiKey;
    /** API Key 所属的规范化 endpoint；不匹配时禁止使用。 */
    private String apiKeyBaseUrl;
    private String model;
    private Integer timeoutSeconds;
    private Integer supportsVision;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
