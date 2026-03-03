-- 库存表初始化脚本
-- Flyway V2__add_inventory_table.sql

-- 库存表（独立于秒杀活动表，用于精确管理库存）
CREATE TABLE IF NOT EXISTS inventory (
    seckill_id BIGINT PRIMARY KEY,
    total_stock INT NOT NULL DEFAULT 0,        -- 总库存
    available_stock INT NOT NULL DEFAULT 0,    -- 可用库存
    locked_stock INT NOT NULL DEFAULT 0,       -- 已锁定库存
    sold_stock INT NOT NULL DEFAULT 0,         -- 已售库存
    version INT DEFAULT 0,                     -- 乐观锁版本号
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_non_negative CHECK (available_stock >= 0 AND locked_stock >= 0 AND sold_stock >= 0),
    CONSTRAINT chk_stock_balance CHECK (total_stock = available_stock + locked_stock + sold_stock)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_inventory_seckill_id ON inventory(seckill_id);

-- 添加注释
COMMENT ON TABLE inventory IS '库存表';
COMMENT ON COLUMN inventory.seckill_id IS '秒杀活动 ID';
COMMENT ON COLUMN inventory.total_stock IS '总库存';
COMMENT ON COLUMN inventory.available_stock IS '可用库存';
COMMENT ON COLUMN inventory.locked_stock IS '已锁定库存（预扣减）';
COMMENT ON COLUMN inventory.sold_stock IS '已售库存';
COMMENT ON COLUMN inventory.version IS '乐观锁版本号';

-- 初始化库存数据（从 seckill 表同步）
INSERT INTO inventory (seckill_id, total_stock, available_stock, locked_stock, sold_stock, version)
SELECT id, stock, stock, 0, 0, 0 FROM seckill
ON CONFLICT (seckill_id) DO NOTHING;
