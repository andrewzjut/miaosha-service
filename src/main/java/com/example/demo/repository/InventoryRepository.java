package com.example.demo.repository;

import com.example.demo.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 库存数据访问层
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 带悲观锁的查询
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.seckillId = :seckillId")
    Optional<Inventory> findBySeckillIdWithLock(@Param("seckillId") Long seckillId);

    /**
     * 乐观锁方式锁定库存
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity, " +
           "i.lockedStock = i.lockedStock + :quantity, i.version = i.version + 1 " +
           "WHERE i.seckillId = :seckillId AND i.availableStock >= :quantity AND i.version = :version")
    int lockStockOptimistic(@Param("seckillId") Long seckillId,
                           @Param("quantity") Integer quantity,
                           @Param("version") Integer version);

    /**
     * 乐观锁方式释放库存
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
           "i.lockedStock = i.lockedStock - :quantity, i.version = i.version + 1 " +
           "WHERE i.seckillId = :seckillId AND i.lockedStock >= :quantity AND i.version = :version")
    int releaseStockOptimistic(@Param("seckillId") Long seckillId,
                               @Param("quantity") Integer quantity,
                               @Param("version") Integer version);

    /**
     * 乐观锁方式确认销售
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.lockedStock = i.lockedStock - :quantity, " +
           "i.soldStock = i.soldStock + :quantity, i.version = i.version + 1 " +
           "WHERE i.seckillId = :seckillId AND i.lockedStock >= :quantity AND i.version = :version")
    int confirmSaleOptimistic(@Param("seckillId") Long seckillId,
                              @Param("quantity") Integer quantity,
                              @Param("version") Integer version);

    /**
     * 获取库存
     */
    @Query("SELECT i.availableStock FROM Inventory i WHERE i.seckillId = :seckillId")
    Integer getAvailableStockBySeckillId(@Param("seckillId") Long seckillId);
}
