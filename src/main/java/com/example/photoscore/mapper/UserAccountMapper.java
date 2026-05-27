package com.example.photoscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.photoscore.pojo.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
