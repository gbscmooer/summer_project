package com.campus.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
