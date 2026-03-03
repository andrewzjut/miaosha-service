package com.example.demo.dto;

/**
 * 订单结果封装
 */
public record OrderResult(
    boolean success,
    String message,
    Long orderId
) {
    public static OrderResult success(Long orderId) {
        return new OrderResult(true, "秒杀成功", orderId);
    }

    public static OrderResult fail(String message) {
        return new OrderResult(false, message, null);
    }

    public static OrderResult processing() {
        return new OrderResult(false, "处理中", null);
    }
}
