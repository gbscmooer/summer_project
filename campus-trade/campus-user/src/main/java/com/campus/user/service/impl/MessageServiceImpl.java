package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PageParamUtil;
import com.campus.user.dto.ConversationVO;
import com.campus.user.dto.MessageVO;
import com.campus.user.dto.UnreadCountVO;
import com.campus.user.entity.Conversation;
import com.campus.user.entity.Message;
import com.campus.user.entity.User;
import com.campus.user.mapper.ConversationMapper;
import com.campus.user.mapper.MessageMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.FriendService;
import com.campus.user.service.MessageService;
import com.campus.user.service.UserPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl
        extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private static final int PREVIEW_MAX = 200;
    private static final int CONTENT_MAX = 2000;

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final FriendService friendService;
    private final UserPermissionService userPermissionService;

    @Override
    public ConversationVO getOrCreateConversation(Long userId, Long peerUserId) {
        if (peerUserId == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        if (Objects.equals(userId, peerUserId)) {
            throw new BizException(ResultCode.MESSAGE_PEER_INVALID);
        }
        User peer = userMapper.selectById(peerUserId);
        if (peer == null) {
            throw new BizException(ResultCode.MESSAGE_PEER_INVALID);
        }

        Conversation conversation = findOrCreateConversation(userId, peerUserId);
        return toConversationVO(conversation, userId, peer);
    }

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .and(w -> w.eq(Conversation::getUserLowId, userId)
                                .or()
                                .eq(Conversation::getUserHighId, userId))
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getId));
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<Long> peerIds = conversations.stream()
                .map(c -> peerIdOf(c, userId))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<ConversationVO> result = new ArrayList<>(conversations.size());
        for (Conversation c : conversations) {
            Long peerId = peerIdOf(c, userId);
            result.add(toConversationVO(c, userId, userMap.get(peerId)));
        }
        return result;
    }

    @Override
    public PageResult<MessageVO> listMessages(Long userId, Long conversationId,
                                              Integer pageNum, Integer pageSize) {
        Conversation conversation = requireParticipant(userId, conversationId);
        int pageNo = PageParamUtil.normalizePageNum(pageNum);
        int size = PageParamUtil.normalizePageSize(pageSize);

        Page<Message> page = new Page<>(pageNo, size);
        Page<Message> result = page(page, new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId())
                .orderByAsc(Message::getCreateTime)
                .orderByAsc(Message::getId));

        List<MessageVO> list = result.getRecords().stream()
                .map(m -> toMessageVO(m, userId))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), pageNo, size, list);
    }

    @Override
    @Transactional
    public MessageVO sendMessage(Long userId, Long conversationId, String content) {
        userPermissionService.requireCanComment(userId);
        Conversation conversation = requireParticipant(userId, conversationId);
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > CONTENT_MAX) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "消息内容长度须为 1–2000 字");
        }

        Long peerId = peerIdOf(conversation, userId);
        if (!friendService.canMessageUnlimited(userId, peerId)) {
            long myCount = count(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .eq(Message::getSenderId, userId));
            if (myCount >= 1) {
                throw new BizException(ResultCode.MESSAGE_LIMIT_REACHED);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setContent(trimmed);
        message.setIsRead(0);
        save(message);

        Conversation patch = new Conversation();
        patch.setId(conversationId);
        patch.setLastMsgPreview(previewOf(trimmed));
        patch.setLastMessageAt(now);
        conversationMapper.updateById(patch);

        return toMessageVO(message, userId);
    }

    @Override
    public void markRead(Long userId, Long conversationId) {
        requireParticipant(userId, conversationId);
        baseMapper.markPeerMessagesRead(conversationId, userId);
    }

    @Override
    public UnreadCountVO unreadCount(Long userId) {
        UnreadCountVO vo = new UnreadCountVO();
        vo.setUnreadCount(baseMapper.countUnreadForUser(userId));
        return vo;
    }

    private Conversation findOrCreateConversation(Long userId, Long peerUserId) {
        long low = Math.min(userId, peerUserId);
        long high = Math.max(userId, peerUserId);
        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserLowId, low)
                        .eq(Conversation::getUserHighId, high));
        if (existing != null) {
            return existing;
        }
        Conversation conversation = new Conversation();
        conversation.setUserLowId(low);
        conversation.setUserHighId(high);
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (Exception ex) {
            // 并发下唯一键冲突：再查一次
            Conversation again = conversationMapper.selectOne(
                    new LambdaQueryWrapper<Conversation>()
                            .eq(Conversation::getUserLowId, low)
                            .eq(Conversation::getUserHighId, high));
            if (again != null) {
                return again;
            }
            throw ex;
        }
    }

    private Conversation requireParticipant(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        if (!Objects.equals(conversation.getUserLowId(), userId)
                && !Objects.equals(conversation.getUserHighId(), userId)) {
            throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private ConversationVO toConversationVO(Conversation conversation, Long userId, User peer) {
        Long peerId = peerIdOf(conversation, userId);
        ConversationVO vo = new ConversationVO();
        vo.setConversationId(conversation.getId());
        vo.setPeerUserId(peerId);
        vo.setPeerNickname(peer != null ? peer.getNickname() : null);
        vo.setPeerAvatar(peer != null ? peer.getAvatar() : null);
        vo.setLastMsgPreview(conversation.getLastMsgPreview());
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setUnreadCount(count(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId())
                .ne(Message::getSenderId, userId)
                .eq(Message::getIsRead, 0)));
        boolean friends = friendService.areFriends(userId, peerId);
        vo.setFriends(friends);
        vo.setCanMessageUnlimited(friends || friendService.isTradeUnlocked(userId, peerId));
        return vo;
    }

    private static MessageVO toMessageVO(Message message, Long userId) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        vo.setMine(Objects.equals(message.getSenderId(), userId));
        return vo;
    }

    private static Long peerIdOf(Conversation conversation, Long userId) {
        return Objects.equals(conversation.getUserLowId(), userId)
                ? conversation.getUserHighId()
                : conversation.getUserLowId();
    }

    private static String previewOf(String content) {
        if (content.length() <= PREVIEW_MAX) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX);
    }
}
