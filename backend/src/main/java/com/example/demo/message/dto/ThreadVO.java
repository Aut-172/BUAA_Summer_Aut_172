package com.example.demo.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 会话线程 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadVO {

    /**
     * 对方ID（商家或用户）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /**
     * 对方类型: user/merchant/rider
     */
    private String targetType;

    /**
     * 关联订单ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 关联订单号
     */
    private String orderNo;

    /**
     * 对方名称
     */
    private String targetName;

    /**
     * 对方头像
     */
    private String targetAvatar;

    /**
     * 最后一条消息内容
     */
    private String lastMessage;

    /**
     * 最后一条消息时间
     */
    private String lastMessageTime;

    /**
     * 未读消息数
     */
    private Integer unreadCount;
}
