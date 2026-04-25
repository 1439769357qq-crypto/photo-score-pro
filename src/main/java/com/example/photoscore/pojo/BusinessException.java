package com.example.photoscore.pojo;

public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private Integer code = 500;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
