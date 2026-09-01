package com.example.demo.common.feign;

import com.example.demo.common.BusinessException;
import lombok.Getter;

/**
 * Stable exception for failed internal service calls.
 */
@Getter
public class RemoteServiceException extends BusinessException {

    private final int httpStatus;
    private final String methodKey;
    private final String requestUrl;
    private final String responseBody;

    public RemoteServiceException(int code, String message, int httpStatus,
                                  String methodKey, String requestUrl, String responseBody) {
        super(code, message);
        this.httpStatus = httpStatus;
        this.methodKey = methodKey;
        this.requestUrl = requestUrl;
        this.responseBody = responseBody;
    }
}
