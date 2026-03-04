package com.example.demo.infrastructure;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebJacksonConfig {

    /**
     * 提供一个全局通用的 ObjectMapper：
     * - 支持 Java 8 时间类型（LocalDateTime 等）
     * - 不启用 Default Typing（避免 ["java.util.HashMap", {...}]）
     * - 忽略 null 值
     * - 使用 ISO 8601 字符串格式化时间（而非时间戳）
     *
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ✅ 注册 JavaTimeModule —— 支持 LocalDateTime, Instant 等
        mapper.registerModule(new JavaTimeModule());

        // ✅ 禁用时间戳格式，使用 ISO-8601 字符串（如 "2026-03-04T11:18:31.393200"）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ 忽略 null 字段
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // ✅ 显式禁用 Default Typing（防止 ["java.util.HashMap", {...}]）
        // 注意：ObjectMapper 默认是关闭的，但为了防御性编程，可显式设置
        mapper.setDefaultTyping(null); // ← 关键：清除任何可能的默认 typing

        return mapper;
    }

}