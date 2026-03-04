package com.example.demo.repository;

import com.example.demo.domain.SeckillResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 秒杀结果数据访问层
 */
@Repository
public interface SeckillResultRepository extends JpaRepository<SeckillResult, Long> {

    /**
     * 检查用户是否已成功参与秒杀
     * @param userId 用户 ID
     * @param seckillId 秒杀活动 ID
     * @return true 表示用户已成功参与过
     */
    @Query("SELECT COUNT(r) > 0 FROM SeckillResult r WHERE r.userId = :userId AND r.seckillId = :seckillId AND r.success = true")
    boolean existsSuccessfulBySeckillId(@Param("userId") Long userId, @Param("seckillId") Long seckillId);
}
