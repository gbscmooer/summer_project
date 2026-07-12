package com.campus.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderTradeMapper {

    @Select("SELECT COUNT(1) FROM t_order "
            + "WHERE status IN (1, 2) "
            + "AND ((buyer_id = #{a} AND seller_id = #{b}) OR (buyer_id = #{b} AND seller_id = #{a}))")
    long countTradeOrders(@Param("a") Long a, @Param("b") Long b);
}
