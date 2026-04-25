package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoScoreRequest {
    @NotNull(message = "请至少上传一张照片")
    @Size(min = 1, max = 50, message = "单次上传照片数量限制为 1-50 张")
    private List<MultipartFile> files;
    private String remark;
}
