package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ps_project")
public class ProjectEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String projectName;
    private String clientName;
    private String shootType;
    private LocalDate shootDate;
    private String note;
    private Integer photoCount;
    private BigDecimal avgScore;
    private Integer keepCount;
    private Integer retouchCount;
    private Integer dropCount;
    private Integer selectedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
