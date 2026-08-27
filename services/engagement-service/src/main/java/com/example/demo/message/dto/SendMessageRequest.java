package com.example.demo.message.dto;

import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {

    /**
     * 接收方ID
     */
    private Long receiverId;

    /**
     * 接收方类型: user/merchant/rider
     */
    private String receiverType;

    /**
     * 关联订单ID（可选）
     */
    private Long orderId;

    /**
     * 消息内容
     */
    private String content;
}
