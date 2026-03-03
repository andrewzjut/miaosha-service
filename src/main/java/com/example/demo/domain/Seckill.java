package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体
 */
@Entity
@Table(name = "seckill")
public class Seckill {

    @Id
    private Long id;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Default constructor
    public Seckill() {
    }

    // Builder-style constructor
    public Seckill(Long id, String productName, Integer stock, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.productName = productName;
        this.stock = stock;
        this.startTime = startTime;
        this.endTime = endTime;
        this.version = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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
    public boolean isStarted() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startTime);
    }

    public boolean isEnded() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(endTime);
    }

    public boolean isActive() {
        return isStarted() && !isEnded();
    }

    @Override
    public String toString() {
        return "Seckill{" +
                "id=" + id +
                ", productName='" + productName + '\'' +
                ", stock=" + stock +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", version=" + version +
                '}';
    }
}
