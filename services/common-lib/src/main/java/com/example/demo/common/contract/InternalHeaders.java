package com.example.demo.common.contract;

/**
 * Header names shared by internal service-to-service calls.
 */
public final class InternalHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String CALLER_SERVICE = "X-Caller-Service";

    private InternalHeaders() {
    }
}
