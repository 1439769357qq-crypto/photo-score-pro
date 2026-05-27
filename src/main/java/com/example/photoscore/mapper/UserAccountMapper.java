package com.example.photoscore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.photoscore.pojo.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    @Select("""
            <script>
            SELECT
                u.id AS userId,
                u.username AS username,
                u.phone AS phone,
                u.email AS email,
                u.status AS status,
                u.created_time AS createdTime,
                u.updated_time AS updatedTime,
                u.last_login_time AS lastLoginTime,
                (SELECT COUNT(*) FROM photo_score_record r WHERE r.user_id = u.id) AS scoreCount,
                (SELECT COALESCE(SUM(r.file_size), 0) FROM photo_score_record r WHERE r.user_id = u.id) AS uploadBytes,
                (SELECT ROUND(AVG(r.total_score), 1) FROM photo_score_record r WHERE r.user_id = u.id) AS avgScore,
                (SELECT MAX(r.created_time) FROM photo_score_record r WHERE r.user_id = u.id) AS lastScoreTime,
                (SELECT r.client_ip FROM photo_score_record r WHERE r.user_id = u.id ORDER BY r.created_time DESC LIMIT 1) AS lastClientIp
            FROM user_account u
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        u.username LIKE CONCAT('%', #{keyword}, '%')
                        OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                        OR u.email LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
            </where>
            ORDER BY COALESCE(u.last_login_time, u.updated_time, u.created_time) DESC, u.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> selectAdminUserUsage(@Param("keyword") String keyword,
                                                   @Param("limit") long limit,
                                                   @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM user_account u
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        u.username LIKE CONCAT('%', #{keyword}, '%')
                        OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                        OR u.email LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
            </where>
            </script>
            """)
    long countAdminUserUsage(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM user_account")
    long countAllUsers();

    @Select("SELECT COUNT(*) FROM user_account WHERE last_login_time IS NOT NULL")
    long countLoggedInUsers();

    @Select("SELECT COUNT(DISTINCT user_id) FROM photo_score_record WHERE user_id IS NOT NULL")
    long countScoredUsers();

    @Select("SELECT COUNT(*) FROM photo_score_record")
    long countAllScoreRecords();
}
