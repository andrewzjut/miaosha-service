package com.example.demo.repository;

import com.example.demo.domain.SeckillOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 秒杀订单数据访问层
 */
@Repository
public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {

    /**
     * 根据用户 ID 和秒杀活动 ID 查询订单
     */
    Optional<SeckillOrder> findByUserIdAndSeckillId(@Param("userId") Long userId, @Param("seckillId") Long seckillId);

    /**
     * 查询用户的所有秒杀订单
     */
    List<SeckillOrder> findByUserId(@Param("userId") Long userId);

    /**
     * 查询秒杀活动的所有订单
     */
    List<SeckillOrder> findBySeckillId(@Param("seckillId") Long seckillId);

    /**
     * 检查用户是否已经参加过该秒杀活动
     */
    boolean existsByUserIdAndSeckillId(@Param("userId") Long userId, @Param("seckillId") Long seckillId);
}
