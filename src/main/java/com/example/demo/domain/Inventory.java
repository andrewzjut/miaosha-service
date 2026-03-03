package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 库存实体
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "seckill_id")
    private Long seckillId;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "locked_stock", nullable = false)
    private Integer lockedStock;

    @Column(name = "sold_stock", nullable = false)
    private Integer soldStock;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Default constructor
    public Inventory() {
    }

    // Constructor
    public Inventory(Long seckillId, Integer totalStock, Integer availableStock) {
        this.seckillId = seckillId;
        this.totalStock = totalStock;
        this.availableStock = availableStock;
        this.lockedStock = 0;
        this.soldStock = 0;
        this.version = 0;
    }

    // Getters and Setters
    public Long getSeckillId() {
        return seckillId;
    }

    public void setSeckillId(Long seckillId) {
        this.seckillId = seckillId;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public Integer getLockedStock() {
        return lockedStock;
    }

    public void setLockedStock(Integer lockedStock) {
        this.lockedStock = lockedStock;
    }

    public Integer getSoldStock() {
        return soldStock;
    }

    public void setSoldStock(Integer soldStock) {
        this.soldStock = soldStock;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods

    /**
     * 锁定库存（预扣减）
     * @return true 表示锁定成功
     */
    public boolean tryLockStock(int quantity) {
        if (this.availableStock >= quantity) {
            this.availableStock -= quantity;
            this.lockedStock += quantity;
            return true;
        }
        return false;
    }

    /**
     * 释放锁定的库存
     */
    public void releaseLockedStock(int quantity) {
        this.lockedStock -= quantity;
        this.availableStock += quantity;
    }

    /**
     * 确认销售（从锁定转为已售）
     */
    public void confirmSale(int quantity) {
        this.lockedStock -= quantity;
        this.soldStock += quantity;
    }

    /**
     * 检查库存是否一致
     */
    public boolean isConsistent() {
        return totalStock.equals(availableStock + lockedStock + soldStock);
    }

    @Override
    public String toString() {
        return "Inventory{" +
                "seckillId=" + seckillId +
                ", totalStock=" + totalStock +
                ", availableStock=" + availableStock +
                ", lockedStock=" + lockedStock +
                ", soldStock=" + soldStock +
                ", version=" + version +
                '}';
    }
}
