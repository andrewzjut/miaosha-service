package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 秒杀结果实体（用于前端轮询）
 */
@Entity
@Table(name = "seckill_result")
public class SeckillResult {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "seckill_id", nullable = false)
    private Long seckillId;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Default constructor
    public SeckillResult() {
        this.success = false;
    }

    // Constructor
    public SeckillResult(Long userId, Long seckillId, Boolean success) {
        this.userId = userId;
        this.seckillId = seckillId;
        this.success = success;
    }

    // Getters and Setters
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

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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
    public static SeckillResult processing(Long userId, Long seckillId) {
        SeckillResult result = new SeckillResult(userId, seckillId, false);
        result.setMessage("处理中");
        return result;
    }

    public static SeckillResult success(Long userId, Long seckillId, Long orderId) {
        SeckillResult result = new SeckillResult(userId, seckillId, true);
        result.setOrderId(orderId);
        result.setMessage("秒杀成功");
        return result;
    }

    public static SeckillResult fail(Long userId, Long seckillId, String message) {
        SeckillResult result = new SeckillResult(userId, seckillId, false);
        result.setMessage(message);
        return result;
    }

    @Override
    public String toString() {
        return "SeckillResult{" +
                "userId=" + userId +
                ", seckillId=" + seckillId +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", orderId=" + orderId +
                '}';
    }
}
