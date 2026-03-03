package com.example.demo.service;

import com.example.demo.domain.Inventory;
import com.example.demo.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 库存服务
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * 初始化库存
     */
    @Transactional
    public void initInventory(Long seckillId, Integer stock) {
        Inventory inventory = new Inventory(seckillId, stock, stock);
        inventoryRepository.save(inventory);
        log.info("初始化库存：seckillId={}, stock={}", seckillId, stock);
    }

    /**
     * 获取库存（不 locks）
     */
    public Inventory getInventory(Long seckillId) {
        return inventoryRepository.findById(seckillId).orElse(null);
    }

    /**
     * 乐观锁方式锁定库存
     * @return true 表示锁定成功
     */
    @Transactional
    public boolean tryLockStock(Long seckillId, Integer quantity, Integer version) {
        int affected = inventoryRepository.lockStockOptimistic(seckillId, quantity, version);
        if (affected > 0) {
            log.info("锁定库存成功：seckillId={}, quantity={}", seckillId, quantity);
            return true;
        }
        log.warn("锁定库存失败：seckillId={}, quantity={}", seckillId, quantity);
        return false;
    }

    /**
     * 乐观锁方式释放库存
     */
    @Transactional
    public boolean releaseStock(Long seckillId, Integer quantity, Integer version) {
        int affected = inventoryRepository.releaseStockOptimistic(seckillId, quantity, version);
        if (affected > 0) {
            log.info("释放库存成功：seckillId={}, quantity={}", seckillId, quantity);
            return true;
        }
        log.warn("释放库存失败：seckillId={}, quantity={}", seckillId, quantity);
        return false;
    }

    /**
     * 乐观锁方式确认销售
     */
    @Transactional
    public boolean confirmSale(Long seckillId, Integer quantity, Integer version) {
        int affected = inventoryRepository.confirmSaleOptimistic(seckillId, quantity, version);
        if (affected > 0) {
            log.info("确认销售成功：seckillId={}, quantity={}", seckillId, quantity);
            return true;
        }
        log.warn("确认销售失败：seckillId={}, quantity={}", seckillId, quantity);
        return false;
    }

    /**
     * 获取当前库存状态（用于验证）
     */
    public InventoryStatus getStatus(Long seckillId) {
        Optional<Inventory> optional = inventoryRepository.findById(seckillId);
        if (optional.isPresent()) {
            Inventory inv = optional.get();
            return new InventoryStatus(
                    inv.getSeckillId(),
                    inv.getTotalStock(),
                    inv.getAvailableStock(),
                    inv.getLockedStock(),
                    inv.getSoldStock()
            );
        }
        return null;
    }

    /**
     * 重置库存（用于测试）
     */
    @Transactional
    public void resetInventory(Long seckillId, Integer totalStock) {
        Inventory inventory = inventoryRepository.findById(seckillId)
                .orElse(new Inventory(seckillId, totalStock, totalStock));
        inventory.setAvailableStock(totalStock);
        inventory.setLockedStock(0);
        inventory.setSoldStock(0);
        inventoryRepository.save(inventory);
        log.info("重置库存：seckillId={}, totalStock={}", seckillId, totalStock);
    }

    /**
     * 库存状态 DTO
     */
    public record InventoryStatus(
            Long seckillId,
            Integer totalStock,
            Integer availableStock,
            Integer lockedStock,
            Integer soldStock
    ) {
        public boolean isConsistent() {
            return totalStock.equals(availableStock + lockedStock + soldStock);
        }
    }
}
