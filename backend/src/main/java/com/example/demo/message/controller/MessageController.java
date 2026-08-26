package com.example.demo.message.controller;

import com.example.demo.common.Result;
import com.example.demo.common.BusinessException;
import com.example.demo.message.dto.MessageVO;
import com.example.demo.message.dto.SendMessageRequest;
import com.example.demo.message.dto.ThreadVO;
import com.example.demo.message.service.MessageService;
import com.example.demo.order.dto.OrderVO;
import com.example.demo.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器
 * 提供用户与商家之间的即时通讯功能（HTTP轮询）
 */
@Tag(name = "会话服务")
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final OrderService orderService;

    public MessageController(MessageService messageService, OrderService orderService) {
        this.messageService = messageService;
        this.orderService = orderService;
    }

    @Operation(summary = "发送消息")
    @PostMapping
    public Result<MessageVO> sendMessage(@RequestBody SendMessageRequest request,
                                         HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        if (userId == null || role == null) {
            return Result.unauthorized("请先登录");
        }

        // 将角色映射为 senderType
        String senderType = mapRoleToType(role);

        MessageVO message = messageService.sendMessage(userId, senderType, request);
        return Result.success(message);
    }

    @Operation(summary = "获取会话消息列表")
    @GetMapping
    public Result<List<MessageVO>> getMessages(
            @RequestParam Long targetId,
            @RequestParam String targetType,
            @RequestParam(required = false) Long orderId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        if (userId == null || role == null) {
            return Result.unauthorized("请先登录");
        }

        String userType = mapRoleToType(role);
        List<MessageVO> messages = messageService.getMessages(userId, userType, targetId, targetType, orderId);
        return Result.success(messages);
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/threads")
    public Result<List<ThreadVO>> getThreads(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        if (userId == null || role == null) {
            return Result.unauthorized("请先登录");
        }

        String userType = mapRoleToType(role);
        List<ThreadVO> threads = messageService.getThreads(userId, userType);
        return Result.success(threads);
    }

    @Operation(summary = "获取未读消息数")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        if (userId == null || role == null) {
            return Result.unauthorized("请先登录");
        }

        String userType = mapRoleToType(role);
        int count = messageService.getUnreadCount(userId, userType);
        return Result.success(count);
    }

    @Operation(summary = "获取会话关联订单详情")
    @GetMapping("/orders/{orderId}")
    public Result<OrderVO> getConversationOrder(@PathVariable Long orderId,
                                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        if (userId == null || role == null) {
            return Result.unauthorized("请先登录");
        }

        String userType = mapRoleToType(role);
        return Result.success(orderService.getParticipantOrderDetail(userId, userType, orderId));
    }

    /**
     * 将角色映射为消息系统中的类型
     */
    private String mapRoleToType(String role) {
        switch (role) {
            case "consumer":
                return "user";
            case "merchant":
                return "merchant";
            case "rider":
                return "rider";
            default:
                throw BusinessException.forbidden("当前角色不支持消息功能");
        }
    }
}
