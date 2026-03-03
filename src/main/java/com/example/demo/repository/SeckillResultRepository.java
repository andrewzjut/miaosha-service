package com.example.demo.repository;

import com.example.demo.domain.SeckillResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 秒杀结果数据访问层
 */
@Repository
public interface SeckillResultRepository extends JpaRepository<SeckillResult, Long> {
    // 主键即 user_id，使用默认 CRUD 方法即可
}
