package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder().code(200).message("success").data(data).timestamp(System.currentTimeMillis()).build();
    }
    public static <T> Result<T> success() {
        return success(null);
    }
    public static <T> Result<T> error(String message) {
        return Result.<T>builder().code(500).message(message).timestamp(System.currentTimeMillis()).build();
    }
    public static <T> Result<T> badRequest(String message) {
        return Result.<T>builder().code(400).message(message).timestamp(System.currentTimeMillis()).build();
    }
}