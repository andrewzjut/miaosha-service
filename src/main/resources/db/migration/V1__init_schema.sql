-- 秒杀系统初始化脚本
-- Flyway V1__init_schema.sql

-- 秒杀活动表
CREATE TABLE IF NOT EXISTS seckill (
    id BIGINT PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock CHECK (stock >= 0),
    CONSTRAINT chk_time CHECK (end_time > start_time)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_seckill_start_time ON seckill(start_time);
CREATE INDEX IF NOT EXISTS idx_seckill_end_time ON seckill(end_time);

-- 订单表
CREATE TABLE IF NOT EXISTS seckill_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    seckill_id BIGINT NOT NULL,
    status SMALLINT DEFAULT 0, -- 0: 处理中，1: 成功，2: 失败
    message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_seckill UNIQUE (user_id, seckill_id),
    CONSTRAINT fk_order_seckill FOREIGN KEY (seckill_id) REFERENCES seckill(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_order_user_id ON seckill_order(user_id);
CREATE INDEX IF NOT EXISTS idx_order_seckill_id ON seckill_order(seckill_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON seckill_order(status);
CREATE INDEX IF NOT EXISTS idx_order_created_at ON seckill_order(created_at);

-- 秒杀结果表（用于前端轮询）
CREATE TABLE IF NOT EXISTS seckill_result (
    user_id BIGINT PRIMARY KEY,
    seckill_id BIGINT NOT NULL,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(500),
    order_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_seckill FOREIGN KEY (seckill_id) REFERENCES seckill(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_result_user_id ON seckill_result(user_id);

-- 添加注释
COMMENT ON TABLE seckill IS '秒杀活动表';
COMMENT ON COLUMN seckill.id IS '秒杀活动 ID';
COMMENT ON COLUMN seckill.product_name IS '商品名称';
COMMENT ON COLUMN seckill.stock IS '库存数量';
COMMENT ON COLUMN seckill.start_time IS '开始时间';
COMMENT ON COLUMN seckill.end_time IS '结束时间';
COMMENT ON COLUMN seckill.version IS '版本号（乐观锁）';

COMMENT ON TABLE seckill_order IS '秒杀订单表';
COMMENT ON COLUMN seckill_order.id IS '订单 ID';
COMMENT ON COLUMN seckill_order.user_id IS '用户 ID';
COMMENT ON COLUMN seckill_order.seckill_id IS '秒杀活动 ID';
COMMENT ON COLUMN seckill_order.status IS '订单状态：0-处理中，1-成功，2-失败';
COMMENT ON COLUMN seckill_order.message IS '处理结果消息';

COMMENT ON TABLE seckill_result IS '秒杀结果缓存表';
COMMENT ON COLUMN seckill_result.success IS '是否秒杀成功';
COMMENT ON COLUMN seckill_result.order_id IS '订单 ID（成功时）';
