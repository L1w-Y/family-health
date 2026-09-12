-- schema.sql · 托管 PostgreSQL 建表脚本（部署第一步执行）
-- 契约：docs/02-数据库与同步.md §2 通用列、§3 表定义；docs/04-技术选型.md §4 PG 落地要点
-- 说明：02 文档为逻辑模型，本脚本为物理落地：uuid 用 TEXT（客户端生成，免 pgcrypto 依赖），
--       date 用 DATE，epoch ms 用 BIGINT，jsonb 用原生 JSONB。

BEGIN;

-- 02 §4.1 / 04 §4.2：全局 seq 序列（store.Connect 也会幂等创建，此处显式声明便于审阅）
CREATE SEQUENCE IF NOT EXISTS sync_seq;

-- 02 §3.1
CREATE TABLE IF NOT EXISTS families (
  id   TEXT PRIMARY KEY,
  name TEXT NOT NULL
);

-- 02 §3.2（§3.13 api_tokens 已并入）
CREATE TABLE IF NOT EXISTS devices (
  id           TEXT PRIMARY KEY,
  family_id    TEXT NOT NULL REFERENCES families(id),
  created_by   TEXT NOT NULL DEFAULT '',
  source       TEXT NOT NULL DEFAULT 'app',
  created_at   BIGINT NOT NULL,
  updated_at   BIGINT NOT NULL,
  deleted      BOOLEAN NOT NULL DEFAULT false,
  seq          BIGINT NOT NULL DEFAULT 0,
  type         TEXT NOT NULL CHECK (type IN ('member','api')),
  display_name TEXT NOT NULL,
  token_hash   TEXT NOT NULL UNIQUE,
  revoked_at   BIGINT,
  last_seen_at BIGINT
);
CREATE INDEX IF NOT EXISTS idx_devices_family ON devices(family_id);

-- 02 §3.3
CREATE TABLE IF NOT EXISTS profiles (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  name TEXT NOT NULL, relation TEXT NOT NULL DEFAULT '',
  gender TEXT NOT NULL DEFAULT 'unknown' CHECK (gender IN ('male','female','unknown')),
  birth_date DATE, notes TEXT NOT NULL DEFAULT '', linked_device_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_profiles_family ON profiles(family_id);
CREATE INDEX IF NOT EXISTS idx_profiles_seq ON profiles(seq);

-- 02 §3.4（追加型）
CREATE TABLE IF NOT EXISTS measurements (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('bp','glucose')),
  measured_at BIGINT NOT NULL, tz_offset_min INT NOT NULL DEFAULT 0,
  systolic INT, diastolic INT, heart_rate_bpm INT,
  glucose_mmol NUMERIC(5,2),
  glucose_context TEXT CHECK (glucose_context IS NULL OR glucose_context IN
    ('fasting','before_meal','after_meal_2h','bedtime','random')),
  note TEXT, payload JSONB
);
CREATE INDEX IF NOT EXISTS idx_measurements_query ON measurements(family_id, profile_id, type, measured_at DESC);
CREATE INDEX IF NOT EXISTS idx_measurements_seq ON measurements(seq);

-- 02 §3.5
CREATE TABLE IF NOT EXISTS checkup_events (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, checkup_date DATE NOT NULL,
  hospital TEXT, department TEXT, note TEXT, next_checkup_date DATE,
  medication_changes_note TEXT  -- 02 §3.5：展示性用药备注（03 §3 原字段落列）
);
CREATE INDEX IF NOT EXISTS idx_checkup_events_query ON checkup_events(family_id, profile_id, checkup_date DESC);
CREATE INDEX IF NOT EXISTS idx_checkup_events_seq ON checkup_events(seq);

-- 02 §3.6
CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  event_id TEXT NOT NULL, title TEXT NOT NULL, report_date DATE NOT NULL, conclusion_text TEXT
);
CREATE INDEX IF NOT EXISTS idx_reports_event ON reports(family_id, event_id);
CREATE INDEX IF NOT EXISTS idx_reports_seq ON reports(seq);

-- 02 §3.7（追加型；canonical_name 写入时按 watch_items 预解析冗余）
CREATE TABLE IF NOT EXISTS indicator_items (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  report_id TEXT NOT NULL, item_name TEXT NOT NULL,
  value_numeric NUMERIC, value_text TEXT,
  unit TEXT, reference_range TEXT,
  sort_order INT NOT NULL DEFAULT 0, canonical_name TEXT
);
CREATE INDEX IF NOT EXISTS idx_indicator_items_report ON indicator_items(family_id, report_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_indicator_items_seq ON indicator_items(seq);

-- 02 §3.8
CREATE TABLE IF NOT EXISTS watch_items (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, canonical_name TEXT NOT NULL,
  aliases JSONB NOT NULL DEFAULT '[]', canonical_unit TEXT, unit_factors JSONB,
  sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_watch_items_profile ON watch_items(family_id, profile_id);
CREATE INDEX IF NOT EXISTS idx_watch_items_seq ON watch_items(seq);

-- 02 §3.9
CREATE TABLE IF NOT EXISTS medication_items (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'long_term' CHECK (category IN ('long_term','temporary')),
  med_kind TEXT NOT NULL DEFAULT 'western' CHECK (med_kind IN ('western','tcm')),
  name TEXT NOT NULL, dosage_text TEXT NOT NULL DEFAULT '',
  dose_qty NUMERIC, dose_unit TEXT, dose_times_per_day SMALLINT,
  dose_slots JSONB, start_date DATE NOT NULL, end_date DATE,
  supersedes_id TEXT, change_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_medication_items_query ON medication_items(family_id, profile_id, category, end_date);
CREATE INDEX IF NOT EXISTS idx_medication_items_seq ON medication_items(seq);

-- 02 §3.10
CREATE TABLE IF NOT EXISTS med_changes (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, effective_date DATE NOT NULL,
  note TEXT, linked_event_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_med_changes_profile ON med_changes(family_id, profile_id, effective_date DESC);
CREATE INDEX IF NOT EXISTS idx_med_changes_seq ON med_changes(seq);

-- 02 §3.11
CREATE TABLE IF NOT EXISTS reminders (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('medication','measure','checkup')),
  times JSONB, measure_type TEXT, advance_days JSONB,
  enabled BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_reminders_profile ON reminders(family_id, profile_id);
CREATE INDEX IF NOT EXISTS idx_reminders_seq ON reminders(seq);

-- 02 §3.12（report_id 可空：附件先于报告经独立通道上传，导入时回填归属）
CREATE TABLE IF NOT EXISTS attachments (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  report_id TEXT, file_key TEXT NOT NULL,
  mime TEXT, size_bytes BIGINT, sha256 TEXT,
  upload_state TEXT NOT NULL DEFAULT 'pending' CHECK (upload_state IN ('pending','done'))
);
CREATE INDEX IF NOT EXISTS idx_attachments_report ON attachments(family_id, report_id);
CREATE INDEX IF NOT EXISTS idx_attachments_seq ON attachments(seq);

-- 02 §3.14
CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, text TEXT NOT NULL, done BOOLEAN NOT NULL DEFAULT false,
  remind_at BIGINT, tz_offset_min INT, remind_targets JSONB
);
CREATE INDEX IF NOT EXISTS idx_notes_query ON notes(family_id, profile_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_seq ON notes(seq);

-- 02 §3.15
CREATE TABLE IF NOT EXISTS daily_med_items (
  id TEXT PRIMARY KEY, family_id TEXT NOT NULL, created_by TEXT NOT NULL,
  source TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT false, seq BIGINT NOT NULL,
  profile_id TEXT NOT NULL, medication_item_id TEXT NOT NULL,
  stock_by_slot JSONB NOT NULL DEFAULT '{}',
  stock_counted_at BIGINT NOT NULL, tz_offset_min INT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_daily_med_items_profile ON daily_med_items(family_id, profile_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_med_items_medication ON daily_med_items(family_id, profile_id, medication_item_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_daily_med_items_seq ON daily_med_items(seq);

-- 03 §1 原则 2：导入幂等（同 import_id 重放返回首次结果）
CREATE TABLE IF NOT EXISTS import_records (
  family_id    TEXT NOT NULL,
  import_id    TEXT NOT NULL,
  device_id    TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  response     JSONB NOT NULL,
  created_at   BIGINT NOT NULL,
  PRIMARY KEY (family_id, import_id)
);

-- 02 §4.2：上行写入幂等键
CREATE TABLE IF NOT EXISTS idempotency_keys (
  device_id  TEXT NOT NULL,
  key        TEXT NOT NULL,
  response   JSONB NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (device_id, key)
);

COMMIT;
