package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 */
@Entity
@Table(name = "seckill_order",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "seckill_id"}, name = "uk_user_seckill"))
public class SeckillOrder {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "seckill_id", nullable = false)
    private Long seckillId;

    @Column(name = "status", nullable = false)
    private Short status; // 0: 处理中，1: 成功，2: 失败

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Default constructor
    public SeckillOrder() {
        this.status = 0;
    }

    // Constructor
    public SeckillOrder(Long id, Long userId, Long seckillId) {
        this.id = id;
        this.userId = userId;
        this.seckillId = seckillId;
        this.status = 0; // 处理中
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSeckillId() {
        return seckillId;
    }

    public void setSeckillId(Long seckillId) {
        this.seckillId = seckillId;
    }

    public Short getStatus() {
        return status;
    }

    public void setStatus(Short status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
    public boolean isProcessing() {
        return this.status == 0;
    }

    public boolean isSuccess() {
        return this.status == 1;
    }

    public boolean isFailed() {
        return this.status == 2;
    }

    public void markSuccess() {
        this.status = 1;
    }

    public void markFailed(String errorMsg) {
        this.status = 2;
        this.message = errorMsg;
    }

    @Override
    public String toString() {
        return "SeckillOrder{" +
                "id=" + id +
                ", userId=" + userId +
                ", seckillId=" + seckillId +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}
