package com.example.photoscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.photoscore.pojo.PhotoScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PhotoScoreRecordMapper extends BaseMapper<PhotoScoreRecord> {

    @Select("SELECT * FROM photo_score_record WHERE file_hash = #{fileHash} LIMIT 1")
    PhotoScoreRecord selectByFileHash(@Param("fileHash") String fileHash);
}