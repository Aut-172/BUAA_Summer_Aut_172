package com.example.demo.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.message.dto.MessageVO;
import com.example.demo.message.dto.SendMessageRequest;
import com.example.demo.message.dto.ThreadVO;
import com.example.demo.message.entity.Message;
import com.example.demo.message.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple polling-based messaging service.
 */
@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;

    public MessageService(MessageMapper messageMapper,
                          UserMapper userMapper,
                          MerchantMapper merchantMapper,
                          RiderMapper riderMapper) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.riderMapper = riderMapper;
    }

    @Transactional
    public MessageVO sendMessage(Long senderId, String senderType, SendMessageRequest request) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setReceiverId(request.getReceiverId());
        message.setReceiverType(request.getReceiverType());
        message.setOrderId(request.getOrderId());
        message.setContent(request.getContent());
        message.setIsRead(false);
        messageMapper.insert(message);
        return toMessageVO(message);
    }

    public List<MessageVO> getMessages(Long userId, String userType, Long targetId, Long orderId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .and(w -> w
                        .and(q -> q.eq(Message::getSenderId, userId)
                                .eq(Message::getSenderType, userType)
                                .eq(Message::getReceiverId, targetId))
                        .or(q -> q.eq(Message::getSenderId, targetId)
                                .eq(Message::getReceiverId, userId)
                                .eq(Message::getReceiverType, userType))
                )
                .orderByAsc(Message::getCreateTime);

        if (orderId != null) {
            wrapper.eq(Message::getOrderId, orderId);
        }

        List<Message> messages = messageMapper.selectList(wrapper);

        List<Message> unreadMessages = messages.stream()
                .filter(message -> !Boolean.TRUE.equals(message.getIsRead()))
                .filter(message -> userId.equals(message.getReceiverId()))
                .filter(message -> userType.equals(message.getReceiverType()))
                .collect(Collectors.toList());
        for (Message message : unreadMessages) {
            message.setIsRead(true);
            messageMapper.updateById(message);
        }

        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    public List<ThreadVO> getThreads(Long userId, String userType) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .and(w -> w
                                .and(q -> q.eq(Message::getSenderId, userId)
                                        .eq(Message::getSenderType, userType))
                                .or(q -> q.eq(Message::getReceiverId, userId)
                                        .eq(Message::getReceiverType, userType))
                        )
                        .orderByDesc(Message::getCreateTime)
        );

        Map<String, List<Message>> grouped = new LinkedHashMap<>();
        for (Message message : messages) {
            Long otherId;
            String otherType;
            if (message.getSenderId().equals(userId) && userType.equals(message.getSenderType())) {
                otherId = message.getReceiverId();
                otherType = message.getReceiverType();
            } else {
                otherId = message.getSenderId();
                otherType = message.getSenderType();
            }
            grouped.computeIfAbsent(otherType + "_" + otherId, key -> new ArrayList<>()).add(message);
        }

        List<ThreadVO> threads = new ArrayList<>();
        for (Map.Entry<String, List<Message>> entry : grouped.entrySet()) {
            List<Message> threadMessages = entry.getValue();
            Message latest = threadMessages.get(0);

            String[] parts = entry.getKey().split("_", 2);
            String otherType = parts[0];
            Long otherId = Long.parseLong(parts[1]);

            String targetName = "";
            String targetAvatar = "";
            if ("user".equals(otherType)) {
                User user = userMapper.selectById(otherId);
                if (user != null) {
                    targetName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                    targetAvatar = user.getAvatar();
                }
            } else if ("merchant".equals(otherType)) {
                Merchant merchant = merchantMapper.selectById(otherId);
                if (merchant != null) {
                    targetName = merchant.getName();
                    targetAvatar = merchant.getAvatar();
                }
            } else if ("rider".equals(otherType)) {
                Rider rider = riderMapper.selectById(otherId);
                if (rider != null) {
                    targetName = rider.getName();
                }
            }

            int unreadCount = (int) threadMessages.stream()
                    .filter(message -> !Boolean.TRUE.equals(message.getIsRead()))
                    .filter(message -> userId.equals(message.getReceiverId()))
                    .filter(message -> userType.equals(message.getReceiverType()))
                    .count();

            threads.add(ThreadVO.builder()
                    .targetId(otherId)
                    .targetName(targetName)
                    .targetAvatar(targetAvatar)
                    .lastMessage(latest.getContent())
                    .lastMessageTime(formatTime(latest.getCreateTime()))
                    .unreadCount(unreadCount)
                    .build());
        }

        return threads;
    }

    public int getUnreadCount(Long userId, String userType) {
        return messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getReceiverId, userId)
                        .eq(Message::getReceiverType, userType)
                        .eq(Message::getIsRead, false)
        ).intValue();
    }

    private MessageVO toMessageVO(Message message) {
        return MessageVO.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .senderType(message.getSenderType())
                .receiverId(message.getReceiverId())
                .receiverType(message.getReceiverType())
                .orderId(message.getOrderId())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createTime(message.getCreateTime())
                .build();
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
