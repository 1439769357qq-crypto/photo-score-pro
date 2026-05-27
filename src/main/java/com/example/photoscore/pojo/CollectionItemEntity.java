package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ps_collection_item")
public class CollectionItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String photoKey;
    private String fileName;
    private BigDecimal originalScore;
    private BigDecimal finalScore;
    private String finalStatus;
    private String note;
    private LocalDateTime createdAt;
}
