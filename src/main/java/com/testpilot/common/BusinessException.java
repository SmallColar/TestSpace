package com.testpilot.common;

import lombok.Getter;

/**
 * 业务异常，携带统一错误码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
