package com.example.demo.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.message.dto.MessageVO;
import com.example.demo.message.dto.SendMessageRequest;
import com.example.demo.message.dto.ThreadVO;
import com.example.demo.message.entity.Message;
import com.example.demo.message.mapper.MessageMapper;
import com.example.demo.order.entity.Orders;
import com.example.demo.order.mapper.OrdersMapper;
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
    private final OrdersMapper ordersMapper;

    public MessageService(MessageMapper messageMapper,
                          UserMapper userMapper,
                          MerchantMapper merchantMapper,
                          RiderMapper riderMapper,
                          OrdersMapper ordersMapper) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.riderMapper = riderMapper;
        this.ordersMapper = ordersMapper;
    }

    @Transactional
    public MessageVO sendMessage(Long senderId, String senderType, SendMessageRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("消息内容不能为空");
        }
        String receiverType = normalizeParticipantType(request.getReceiverType());
        String content = normalizeContent(request.getContent());
        validateTargetExists(request.getReceiverId(), receiverType);
        if (senderId.equals(request.getReceiverId()) && senderType.equals(receiverType)) {
            throw BusinessException.badRequest("不能给自己发送消息");
        }
        validateOrderConversation(senderId, senderType, request.getReceiverId(), receiverType, request.getOrderId(), true);

        Message message = new Message();
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setReceiverId(request.getReceiverId());
        message.setReceiverType(receiverType);
        message.setOrderId(request.getOrderId());
        message.setContent(content);
        message.setIsRead(false);
        messageMapper.insert(message);
        return toMessageVO(message);
    }

    public List<MessageVO> getMessages(Long userId, String userType, Long targetId, String targetType, Long orderId) {
        String normalizedTargetType = normalizeParticipantType(targetType);
        validateTargetExists(targetId, normalizedTargetType);
        validateOrderConversation(userId, userType, targetId, normalizedTargetType, orderId, false);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .and(w -> w
                        .and(q -> q.eq(Message::getSenderId, userId)
                                .eq(Message::getSenderType, userType)
                                .eq(Message::getReceiverId, targetId)
                                .eq(Message::getReceiverType, normalizedTargetType))
                        .or(q -> q.eq(Message::getSenderId, targetId)
                                .eq(Message::getSenderType, normalizedTargetType)
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
            String orderKey = message.getOrderId() != null ? String.valueOf(message.getOrderId()) : "none";
            grouped.computeIfAbsent(otherType + "_" + otherId + "_" + orderKey, key -> new ArrayList<>()).add(message);
        }

        List<ThreadVO> threads = new ArrayList<>();
        for (Map.Entry<String, List<Message>> entry : grouped.entrySet()) {
            List<Message> threadMessages = entry.getValue();
            Message latest = threadMessages.get(0);

            String[] parts = entry.getKey().split("_", 3);
            String otherType = parts[0];
            Long otherId = Long.parseLong(parts[1]);

            int unreadCount = (int) threadMessages.stream()
                    .filter(message -> !Boolean.TRUE.equals(message.getIsRead()))
                    .filter(message -> userId.equals(message.getReceiverId()))
                    .filter(message -> userType.equals(message.getReceiverType()))
                    .count();

            threads.add(ThreadVO.builder()
                    .targetId(otherId)
                    .targetType(otherType)
                    .orderId(latest.getOrderId())
                    .orderNo(getOrderNo(latest.getOrderId()))
                    .targetName(getTargetName(otherId, otherType))
                    .targetAvatar(getTargetAvatar(otherId, otherType))
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

    private String normalizeParticipantType(String type) {
        if (type == null || type.isBlank()) {
            throw BusinessException.badRequest("请选择接收方类型");
        }
        return switch (type.trim()) {
            case "user", "merchant", "rider" -> type.trim();
            default -> throw BusinessException.badRequest("接收方类型不合法");
        };
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("消息内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 500) {
            throw BusinessException.badRequest("消息内容不能超过500字");
        }
        return trimmed;
    }

    private void validateTargetExists(Long targetId, String targetType) {
        if (targetId == null) {
            throw BusinessException.badRequest("请选择接收方");
        }
        boolean exists = switch (targetType) {
            case "user" -> userMapper.selectById(targetId) != null;
            case "merchant" -> merchantMapper.selectById(targetId) != null;
            case "rider" -> riderMapper.selectById(targetId) != null;
            default -> false;
        };
        if (!exists) {
            throw BusinessException.notFound("接收方不存在");
        }
    }

    private void validateOrderConversation(Long actorId,
                                           String actorType,
                                           Long targetId,
                                           String targetType,
                                           Long orderId,
                                           boolean requireOrderId) {
        if (orderId == null) {
            if (requireOrderId) {
                throw BusinessException.badRequest("请选择关联订单");
            }
            return;
        }

        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!isOrderParticipant(order, actorId, actorType)) {
            throw BusinessException.forbidden("无权访问该订单会话");
        }
        if (!isOrderParticipant(order, targetId, targetType)) {
            throw BusinessException.badRequest("接收方与订单无关");
        }
    }

    private boolean isOrderParticipant(Orders order, Long participantId, String participantType) {
        if (participantId == null || participantType == null) {
            return false;
        }
        return switch (participantType) {
            case "user" -> participantId.equals(order.getUserId());
            case "merchant" -> participantId.equals(order.getMerchantId());
            case "rider" -> participantId.equals(order.getRiderId());
            default -> false;
        };
    }

    private String getTargetName(Long targetId, String targetType) {
        if ("user".equals(targetType)) {
            User user = userMapper.selectById(targetId);
            if (user != null) {
                return user.getNickname() != null ? user.getNickname() : user.getUsername();
            }
        } else if ("merchant".equals(targetType)) {
            Merchant merchant = merchantMapper.selectById(targetId);
            if (merchant != null) {
                return merchant.getName();
            }
        } else if ("rider".equals(targetType)) {
            Rider rider = riderMapper.selectById(targetId);
            if (rider != null) {
                return rider.getName();
            }
        }
        return "未知联系人";
    }

    private String getTargetAvatar(Long targetId, String targetType) {
        if ("user".equals(targetType)) {
            User user = userMapper.selectById(targetId);
            return user != null ? user.getAvatar() : "";
        }
        if ("merchant".equals(targetType)) {
            Merchant merchant = merchantMapper.selectById(targetId);
            return merchant != null ? merchant.getAvatar() : "";
        }
        return "";
    }

    private String getOrderNo(Long orderId) {
        if (orderId == null) {
            return null;
        }
        Orders order = ordersMapper.selectById(orderId);
        return order != null ? order.getOrderNo() : null;
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
