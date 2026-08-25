package com.example.demo.delivery.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配送追踪 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private String status;
    private String riderName;
    private String riderPhone;
    private String eta;
    private List<TimelineItem> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {
        private String label;
        private String time;
    }
}
