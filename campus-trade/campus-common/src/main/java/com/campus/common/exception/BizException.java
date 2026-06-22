package com.campus.common.exception;

import com.campus.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }
}
