package com.campus.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE t_product SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(Long id);

    @Update("UPDATE t_product SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int deductStock(Long id);

    @Update("UPDATE t_product SET stock = stock + 1 WHERE id = #{id}")
    void restoreStock(Long id);
}
