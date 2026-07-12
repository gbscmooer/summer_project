package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子更新卖家信誉：并发评价时不会丢 count / 算错 avg。
     */
    @Update("""
            UPDATE t_user
            SET review_count = IFNULL(review_count, 0) + 1,
                avg_rating = ROUND(
                    (IFNULL(avg_rating, 0) * IFNULL(review_count, 0) + #{rating})
                    / (IFNULL(review_count, 0) + 1),
                    2)
            WHERE id = #{sellerId}
            """)
    int applyRatingAtomic(@Param("sellerId") Long sellerId, @Param("rating") int rating);
}
