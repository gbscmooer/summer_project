package com.campus.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO {
    private Integer status; // 0: queuing, 1: success, -1: failed
    private String orderNo;
}
