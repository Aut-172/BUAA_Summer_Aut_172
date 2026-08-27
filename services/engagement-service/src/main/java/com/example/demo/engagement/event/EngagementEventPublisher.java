package com.example.demo.engagement.event;

import com.example.demo.review.entity.Review;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EngagementEventPublisher {

    public void publishReviewCreated(List<Review> reviews) {
        for (Review review : reviews) {
            log.info("ReviewCreated event queued: orderId={}, merchantId={}, productId={}, rating={}",
                    review.getOrderId(), review.getMerchantId(), review.getProductId(), review.getRating());
        }
    }

    public void publishMessageSent(Long messageId, Long orderId, Long receiverId, String receiverType) {
        log.info("MessageSent event queued: messageId={}, orderId={}, receiverId={}, receiverType={}",
                messageId, orderId, receiverId, receiverType);
    }
}
