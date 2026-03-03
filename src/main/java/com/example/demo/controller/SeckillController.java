package com.example.demo.controller;

import com.example.demo.domain.Seckill;
import com.example.demo.domain.SeckillOrder;
import com.example.demo.domain.SeckillResult;
import com.example.demo.dto.OrderResult;
import com.example.demo.dto.SeckillRequest;
import com.example.demo.dto.SeckillResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.SeckillRepository;
import com.example.demo.service.SeckillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀控制器
 */
@RestController
@RequestMapping("/api/seckill")
@Validated
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    private final SeckillService seckillService;
    private final SeckillRepository seckillRepository;

    public SeckillController(SeckillService seckillService,
                             SeckillRepository seckillRepository) {
        this.seckillService = seckillService;
        this.seckillRepository = seckillRepository;
    }

    /**
     * 提交秒杀请求
     * POST /api/seckill/{id}
     */
    @PostMapping("/{id}")
    public ResponseEntity<Map<String, Object>> submitSeckill(
            @PathVariable @Positive(message = "秒杀活动 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "用户 ID 必须大于 0") Long userId) {

        log.info("收到秒杀请求：userId={}, seckillId={}", userId, id);

        SeckillRequest request = new SeckillRequest(userId, id);
        OrderResult result = seckillService.processSeckill(request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", result.success());
        body.put("message", result.message());
        if (result.orderId() != null) {
            body.put("orderId", result.orderId());
        }

        return ResponseEntity.ok(body);
    }

    /**
     * 查询秒杀结果
     * GET /api/seckill/{id}/result
     */
    @GetMapping("/{id}/result")
    public ResponseEntity<Map<String, Object>> getSeckillResult(
            @PathVariable @Positive(message = "秒杀活动 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "用户 ID 必须大于 0") Long userId) {

        log.info("查询秒杀结果：userId={}, seckillId={}", userId, id);

        SeckillResult result = seckillService.getSeckillResult(userId);

        Map<String, Object> body = new HashMap<>();
        if (result == null) {
            body.put("success", false);
            body.put("message", "未找到秒杀记录");
            body.put("status", "NOT_FOUND");
        } else {
            body.put("success", result.getSuccess());
            body.put("message", result.getMessage());
            body.put("status", result.getSuccess() ? "SUCCESS" : "PROCESSING");
            if (result.getOrderId() != null) {
                body.put("orderId", result.getOrderId());
            }
        }

        return ResponseEntity.ok(body);
    }

    /**
     * 查询秒杀活动详情
     * GET /api/seckill/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSeckillDetail(
            @PathVariable @Positive(message = "秒杀活动 ID 必须大于 0") Long id) {

        log.info("查询秒杀活动详情：seckillId={}", id);

        Seckill seckill = seckillRepository.findById(id)
                .orElseThrow(() -> new BusinessException("秒杀活动不存在"));

        SeckillResponse response = SeckillResponse.fromSeckill(seckill);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", response);

        return ResponseEntity.ok(body);
    }

    /**
     * 查询秒杀活动列表
     * GET /api/seckill
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSeckills() {
        log.info("查询秒杀活动列表");

        List<Seckill> seckills = seckillRepository.findAllByEndTimeAfterOrderByStartTimeAsc(
                java.time.LocalDateTime.now());

        List<SeckillResponse> responses = seckills.stream()
                .map(SeckillResponse::fromSeckill)
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", responses);
        body.put("total", responses.size());

        return ResponseEntity.ok(body);
    }

    /**
     * 查询用户订单
     * GET /api/seckill/{id}/order
     */
    @GetMapping("/{id}/order")
    public ResponseEntity<Map<String, Object>> getUserOrder(
            @PathVariable @Positive(message = "秒杀活动 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "用户 ID 必须大于 0") Long userId) {

        log.info("查询用户订单：userId={}, seckillId={}", userId, id);

        // 先查结果表
        SeckillResult result = seckillService.getSeckillResult(userId);
        if (result == null || result.getOrderId() == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "未找到订单");
            return ResponseEntity.ok(body);
        }

        // 根据订单 ID 查询
        // 这里简化处理，实际应该注入 OrderService
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", Map.of(
                "orderId", result.getOrderId(),
                "seckillId", result.getSeckillId(),
                "success", result.getSuccess(),
                "message", result.getMessage()
        ));

        return ResponseEntity.ok(body);
    }
}
