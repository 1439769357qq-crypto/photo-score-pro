package com.example.photoscore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProApiResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> ProApiResponse<T> success(T data) {
        return new ProApiResponse<>(200, "success", data);
    }

    public static <T> ProApiResponse<T> fail(String message) {
        return new ProApiResponse<>(500, message, null);
    }

    public static <T> ProApiResponse<T> badRequest(String message) {
        return new ProApiResponse<>(400, message, null);
    }
}