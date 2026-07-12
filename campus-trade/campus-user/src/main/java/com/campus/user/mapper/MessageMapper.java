package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT COUNT(1) FROM t_message m "
            + "INNER JOIN t_conversation c ON c.id = m.conversation_id "
            + "WHERE m.sender_id <> #{userId} AND m.is_read = 0 "
            + "AND (c.user_low_id = #{userId} OR c.user_high_id = #{userId})")
    long countUnreadForUser(@Param("userId") Long userId);

    @Update("UPDATE t_message SET is_read = 1 "
            + "WHERE conversation_id = #{conversationId} "
            + "AND sender_id <> #{userId} AND is_read = 0")
    int markPeerMessagesRead(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId);
}
