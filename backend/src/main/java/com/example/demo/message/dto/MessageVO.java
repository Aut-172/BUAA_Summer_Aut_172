package com.example.demo.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;
    private String senderType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;
    private String receiverType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private String content;
    private Boolean isRead;
    private LocalDateTime createTime;
}
