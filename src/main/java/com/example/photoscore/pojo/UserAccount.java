package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_account")
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String phone;
    private String avatarUrl;
    private String wechatOpenid;
    private String wechatUnionid;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String email;
    private Integer emailVerified;
}
