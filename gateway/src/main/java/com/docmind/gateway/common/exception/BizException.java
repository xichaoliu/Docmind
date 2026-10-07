package com.docmind.gateway.common.exception;

import lombok.Getter;
/**
 * 自定义业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}