package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ps_exif_record")
public class ExifRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String photoKey;
    private String fileName;
    private String cameraMake;
    private String cameraModel;
    private String lensModel;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private String iso;
    private String exposureBias;
    private String shootTime;
    private String gps;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String advice;
    private String rawJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
