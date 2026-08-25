package com.example.demo.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    /**
     * 发送方ID
     */
    private Long senderId;

    /**
     * 发送方类型: user/merchant/rider
     */
    private String senderType;

    /**
     * 接收方ID
     */
    private Long receiverId;

    /**
     * 接收方类型: user/merchant/rider
     */
    private String receiverType;

    /**
     * 关联订单ID
     */
    private Long orderId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 是否已读 (0-未读, 1-已读)
     */
    private Boolean isRead;
}
