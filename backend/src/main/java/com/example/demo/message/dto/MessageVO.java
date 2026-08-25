package com.example.demo.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private Long senderId;
    private String senderType;
    private Long receiverId;
    private String receiverType;
    private Long orderId;
    private String content;
    private Boolean isRead;
    private LocalDateTime createTime;
}
