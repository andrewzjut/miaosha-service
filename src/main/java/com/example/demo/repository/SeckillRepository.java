package com.example.demo.repository;

import com.example.demo.domain.Seckill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 秒杀活动数据访问层
 */
@Repository
public interface SeckillRepository extends JpaRepository<Seckill, Long> {

    /**
     * 查询所有正在进行或即将开始的秒杀活动
     */
    List<Seckill> findAllByEndTimeAfterOrderByStartTimeAsc(LocalDateTime now);

    /**
     * 查询活跃的秒杀活动
     */
    @Query("SELECT s FROM Seckill s WHERE s.startTime <= :now AND s.endTime > :now")
    List<Seckill> findActiveSeckills(@Param("now") LocalDateTime now);

    /**
     * 带悲观锁的查询（用于库存扣减）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seckill s WHERE s.id = :id")
    Optional<Seckill> findByIdWithLock(@Param("id") Long id);

    /**
     * 乐观锁方式扣减库存
     */
    @Modifying
    @Query("UPDATE Seckill s SET s.stock = s.stock - 1, s.version = s.version + 1 WHERE s.id = :id AND s.stock > 0 AND s.version = :version")
    int decrementStockOptimistic(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 检查库存是否充足
     */
    @Query("SELECT s.stock FROM Seckill s WHERE s.id = :id")
    Integer getStockById(@Param("id") Long id);
}
