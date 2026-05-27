package com.example.photoscore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceApiResponse<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> WorkspaceApiResponse<T> success(T data) {
        return new WorkspaceApiResponse<>(200, "success", data);
    }

    public static <T> WorkspaceApiResponse<T> fail(String message) {
        return new WorkspaceApiResponse<>(500, message, null);
    }
}
