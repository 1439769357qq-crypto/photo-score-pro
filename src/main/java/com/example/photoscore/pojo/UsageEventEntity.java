package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ps_usage_event")
public class UsageEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private Integer eventCount;
    private Long projectId;
    private String fileName;
    private String metaJson;
    private LocalDateTime createdAt;
}
