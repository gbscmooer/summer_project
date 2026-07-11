package com.campus.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.order.entity.StockCompensationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StockCompensationTaskMapper extends BaseMapper<StockCompensationTask> {

    @Select("SELECT * FROM t_stock_compensation_task WHERE order_no = #{orderNo} FOR UPDATE")
    StockCompensationTask lockByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM t_stock_compensation_task "
            + "WHERE id = #{id} AND status = 0 AND next_retry_time <= NOW() "
            + "FOR UPDATE SKIP LOCKED")
    StockCompensationTask lockDueById(@Param("id") Long id);
}
