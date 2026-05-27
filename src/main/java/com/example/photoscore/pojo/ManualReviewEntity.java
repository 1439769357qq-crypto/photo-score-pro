package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ps_manual_review")
public class ManualReviewEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String photoKey;
    private String fileName;
    private String manualStatus;
    private BigDecimal manualScore;
    private String manualNote;
    private Boolean overrideAi;
    private Boolean selected;
    private String issueTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
