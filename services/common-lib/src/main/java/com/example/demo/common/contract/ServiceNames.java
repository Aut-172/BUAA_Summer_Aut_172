package com.example.demo.common.contract;

/**
 * Logical service names for Nacos discovery, OpenFeign and Gateway routes.
 */
public final class ServiceNames {

    public static final String USER_SERVICE = "user-service";
    public static final String MERCHANT_SERVICE = "merchant-service";
    public static final String ORDER_SERVICE = "order-service";
    public static final String SETTLEMENT_SERVICE = "settlement-service";
    public static final String FULFILLMENT_SERVICE = "fulfillment-service";
    public static final String ENGAGEMENT_SERVICE = "engagement-service";

    private ServiceNames() {
    }
}
