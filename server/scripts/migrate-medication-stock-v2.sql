-- 契约：docs/02-数据库与同步.md §3.9、§3.15
-- 用药方案结构化一次用量；今日用药旧数据按产品决定不保留，重建为按时段盘点库存。
BEGIN;

ALTER TABLE medication_items ADD COLUMN IF NOT EXISTS dose_qty NUMERIC;
ALTER TABLE medication_items ADD COLUMN IF NOT EXISTS dose_unit TEXT;
ALTER TABLE medication_items ADD COLUMN IF NOT EXISTS dose_times_per_day SMALLINT;

DROP TABLE IF EXISTS daily_med_items;
CREATE TABLE daily_med_items (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, medication_item_id TEXT NOT NULL,
  stock_by_slot JSONB NOT NULL DEFAULT '{}',
  stock_counted_at BIGINT NOT NULL, tz_offset_min INT NOT NULL
);
CREATE INDEX idx_daily_med_items_profile ON daily_med_items(family_id, profile_id);
CREATE UNIQUE INDEX idx_daily_med_items_medication ON daily_med_items(family_id, profile_id, medication_item_id) WHERE deleted = false;
CREATE INDEX idx_daily_med_items_seq ON daily_med_items(seq);

COMMIT;
