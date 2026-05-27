package com.example.photoscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.photoscore.pojo.PhotoScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PhotoScoreRecordMapper extends BaseMapper<PhotoScoreRecord> {

    @Select("SELECT * FROM photo_score_record WHERE file_hash = #{fileHash} LIMIT 1")
    PhotoScoreRecord selectByFileHash(@Param("fileHash") String fileHash);

    @Select("""
            <script>
            SELECT * FROM photo_score_record
            WHERE file_hash IN
            <foreach collection="fileHashes" item="fileHash" open="(" separator="," close=")">
                #{fileHash}
            </foreach>
            </script>
            """)
    List<PhotoScoreRecord> selectByFileHashes(@Param("fileHashes") Collection<String> fileHashes);
}
