package com.campus.user.service;

import com.campus.common.result.PageResult;
import com.campus.user.dto.ConversationVO;
import com.campus.user.dto.MessageVO;
import com.campus.user.dto.UnreadCountVO;

import java.util.List;

public interface MessageService {

    ConversationVO getOrCreateConversation(Long userId, Long peerUserId);

    List<ConversationVO> listConversations(Long userId);

    PageResult<MessageVO> listMessages(Long userId, Long conversationId, Integer pageNum, Integer pageSize);

    MessageVO sendMessage(Long userId, Long conversationId, String content);

    void markRead(Long userId, Long conversationId);

    UnreadCountVO unreadCount(Long userId);
}
