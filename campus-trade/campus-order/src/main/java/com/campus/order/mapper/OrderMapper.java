package com.campus.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper，继承 MyBatis-Plus {@link BaseMapper} 获得单表 CRUD 能力。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
