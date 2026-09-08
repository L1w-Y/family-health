package store

// 表元数据：同步上行白名单（02 §4.2）与通用行读写的列清单。
// 契约：docs/02-数据库与同步.md §3（表定义）、§4.2（表白名单）、§4.3（可改行 LWW）
//
// 列名即数据库物理列名；Base 列（02 §2）由 store 统一处理，不在 BusinessColumns 内。

// TableKind 表的写入形态
type TableKind int

const (
	// KindAppendOnly 追加型：只增（02 §4.2 天然无冲突）；允许软删（02 §1 删除决策）
	KindAppendOnly TableKind = iota
	// KindMutable 可改表：整行 LWW（02 §4.3）
	KindMutable
)

type tableMeta struct {
	kind     TableKind
	bizCols  []string        // 业务列（不含 02 §2 通用列）
	jsonbCol map[string]bool // 其中的 jsonb 列（值需 JSON 序列化入库）
}

// tables 同步上行白名单即本 map 的键集合。
// 可改表集合 = 02 §4.3 明示的 profiles/medication_items/reminders/watch_items，
// 另含 notes（done 勾选）与 daily_med_items（余量手填更新）——两处均依赖整行覆盖才能成立，
// 与 §4.3 的 LWW 语义一致，已在 README「实现决策」记录。
var tables = map[string]tableMeta{
	"profiles": {
		kind:    KindMutable,
		bizCols: []string{"name", "relation", "gender", "birth_date", "notes", "linked_device_id"},
	},
	"measurements": {
		kind: KindAppendOnly,
		bizCols: []string{"profile_id", "type", "measured_at", "tz_offset_min",
			"systolic", "diastolic", "heart_rate_bpm", "glucose_mmol", "glucose_context", "note", "payload"},
		jsonbCol: map[string]bool{"payload": true},
	},
	"checkup_events": {
		// 可改表：复查日期/备注/下次复查日期存在真实修改需求（如医生改期），整行 LWW（02 §4.3）
		kind: KindMutable,
		bizCols: []string{"profile_id", "checkup_date", "hospital", "department", "note",
			"next_checkup_date", "medication_changes_note"},
	},
	"reports": {
		kind:    KindAppendOnly,
		bizCols: []string{"event_id", "title", "report_date", "conclusion_text"},
	},
	"indicator_items": {
		kind: KindAppendOnly,
		bizCols: []string{"report_id", "item_name", "value_numeric", "value_text",
			"unit", "reference_range", "sort_order", "canonical_name"},
	},
	"watch_items": {
		kind:     KindMutable,
		bizCols:  []string{"profile_id", "canonical_name", "aliases", "canonical_unit", "unit_factors", "sort_order"},
		jsonbCol: map[string]bool{"aliases": true, "unit_factors": true},
	},
	"medication_items": {
		kind: KindMutable,
		bizCols: []string{"profile_id", "category", "med_kind", "name", "dosage_text",
			"dose_slots", "start_date", "end_date", "supersedes_id", "change_id"},
		jsonbCol: map[string]bool{"dose_slots": true},
	},
	"med_changes": {
		kind:    KindMutable,
		bizCols: []string{"profile_id", "effective_date", "note", "linked_event_id"},
	},
	"reminders": {
		kind:     KindMutable,
		bizCols:  []string{"profile_id", "type", "times", "measure_type", "advance_days", "enabled"},
		jsonbCol: map[string]bool{"times": true, "advance_days": true},
	},
	"notes": {
		kind:     KindMutable,
		bizCols:  []string{"profile_id", "text", "done", "remind_at", "tz_offset_min", "remind_targets"},
		jsonbCol: map[string]bool{"remind_targets": true},
	},
	"daily_med_items": {
		kind: KindMutable,
		bizCols: []string{"profile_id", "is_tcm", "name", "dose_text", "dose_slots",
			"stock_qty", "stock_unit", "daily_qty", "tcm_packs", "tcm_days_per_pack", "tcm_used_days"},
		jsonbCol: map[string]bool{"dose_slots": true},
	},
	"attachments": {
		kind:    KindAppendOnly,
		bizCols: []string{"report_id", "file_key", "mime", "size_bytes", "sha256", "upload_state"},
	},
}

// BaseCols 02 §2 通用列（所有业务表）
var BaseCols = []string{"id", "family_id", "created_by", "source", "created_at", "updated_at", "deleted", "seq"}

// KnownTable 表是否在同步白名单内（02 §4.2）
func KnownTable(name string) bool {
	_, ok := tables[name]
	return ok
}

// TableKindOf 返回表写入形态；未知表返回 KindAppendOnly + false
func TableKindOf(name string) (TableKind, bool) {
	m, ok := tables[name]
	return m.kind, ok
}

// BusinessColumns 业务列清单（不含通用列）
func BusinessColumns(name string) []string {
	return tables[name].bizCols
}

// AllColumns 通用列 + 业务列
func AllColumns(name string) []string {
	m := tables[name]
	cols := make([]string, 0, len(BaseCols)+len(m.bizCols))
	cols = append(cols, BaseCols...)
	cols = append(cols, m.bizCols...)
	return cols
}

// IsJSONBColumn 该列是否 jsonb（值需序列化/反序列化）
func IsJSONBColumn(table, col string) bool {
	m, ok := tables[table]
	return ok && m.jsonbCol[col]
}
