package com.campus.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {
    private Long productId;
    private Long buyerId;
}
