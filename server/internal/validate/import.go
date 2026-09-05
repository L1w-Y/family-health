// Package validate 导入文本格式 v1 的结构定义与校验。
// 契约：docs/03-导入格式-v1.md（全文）；本包是该契约的代码形态。
// 任何契约变更必须先改 docs/03，再改本包。
package validate

// 契约：03 §1 限制常量（§6.4）
const (
	MaxBodyBytes        = 512 * 1024
	MaxReportsPerEvent  = 30
	MaxIndicatorsPerRep = 200
	MaxMeasurements     = 500
	FormatName          = "family-health-import"
	FormatVersion       = 1
)

// Envelope 导入信封（03 §2）
type Envelope struct {
	Format   string     `json:"format"`
	Version  int        `json:"version"`
	ImportID string     `json:"import_id"`
	DryRun   bool       `json:"dry_run"`
	Profile  ProfileRef `json:"profile_ref"`
	Payload  Payload    `json:"payload"`
}

// ProfileRef 档案定位：id 或 name 二选一（03 §2）
type ProfileRef struct {
	ID   string `json:"id,omitempty"`
	Name string `json:"name,omitempty"`
}

// Payload 三种载荷之一（按 Type 判别）
type Payload struct {
	Type      string            `json:"type"` // checkup_event / measurements / medication_changes
	Event     *EventPayload     `json:"event,omitempty"`
	Items     []MeasurementItem `json:"items,omitempty"`
	ChangeSet *MedChangePayload `json:",omitempty"` // medication_changes 展开字段见 03 §5
}

// EventPayload 载荷 A：复查事件（03 §3）
type EventPayload struct {
	CheckupDate           string          `json:"checkup_date"`
	Hospital              string          `json:"hospital,omitempty"`
	Department            string          `json:"department,omitempty"`
	Note                  string          `json:"note,omitempty"`
	NextCheckupDate       string          `json:"next_checkup_date,omitempty"`
	MedicationChangesNote string          `json:"medication_changes_note,omitempty"`
	Reports               []ReportPayload `json:"reports"`
}

// ReportPayload 报告（03 §3.1）
type ReportPayload struct {
	Title          string             `json:"title"`
	ReportDate     string             `json:"report_date,omitempty"`
	ConclusionText string             `json:"conclusion_text,omitempty"`
	AttachmentIDs  []string           `json:"attachment_ids,omitempty"`
	Indicators     []IndicatorPayload `json:"indicators,omitempty"`
}

// IndicatorPayload 指标项（03 §3.2）；Value 为 number 或 string
type IndicatorPayload struct {
	ItemName       string `json:"item_name"`
	Value          any    `json:"value"`
	Unit           string `json:"unit,omitempty"`
	ReferenceRange string `json:"reference_range,omitempty"`
}

// MeasurementItem 载荷 B：批量测量（03 §4）
type MeasurementItem struct {
	Type         string   `json:"type"` // bp / glucose
	MeasuredAt   string   `json:"measured_at"`
	Systolic     *int     `json:"systolic,omitempty"`
	Diastolic    *int     `json:"diastolic,omitempty"`
	HeartRateBpm *int     `json:"heart_rate_bpm,omitempty"`
	GlucoseMmol  *float64 `json:"glucose_mmol,omitempty"`
	GlucoseCtx   string   `json:"glucose_context,omitempty"`
	Note         string   `json:"note,omitempty"`
}

// MedChangePayload 载荷 C：用药变更（03 §5）
type MedChangePayload struct {
	EffectiveDate string      `json:"effective_date"`
	LinkedEventID string      `json:"linked_event_id,omitempty"`
	ReasonNote    string      `json:"reason_note,omitempty"`
	Stop          []StopItem  `json:"stop,omitempty"`
	Start         []StartItem `json:"start,omitempty"`
}

// StopItem 停用条目（按 id 或 name 匹配当前进行中条目）
type StopItem struct {
	Match   MatchRef `json:"match"`
	EndDate string   `json:"end_date,omitempty"`
}

// MatchRef 匹配引用
type MatchRef struct {
	ID   string `json:"id,omitempty"`
	Name string `json:"name,omitempty"`
}

// StartItem 新增条目
type StartItem struct {
	Category   string   `json:"category,omitempty"` // 缺省 long_term
	Kind       string   `json:"med_kind,omitempty"` // 缺省 western
	Name       string   `json:"name"`
	DosageText string   `json:"dosage_text,omitempty"`
	DoseSlots  []string `json:"dose_slots,omitempty"`
	StartDate  string   `json:"start_date,omitempty"`
	EndDate    string   `json:"end_date,omitempty"`
	Supersedes string   `json:"supersedes,omitempty"`
}

// Error 校验错误（03 §6.3）
type Error struct {
	Path    string `json:"path"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Warning 非阻断警告（03 §6.2）
type Warning struct {
	Path    string `json:"path"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// ParseAndValidate 解析并整体校验（任一字段非法则整体拒绝，03 §1 原则 1）。
// TODO M1：实现。校验规则逐条对应 03 §2~§6.4，测试见同包 *_test.go。
func ParseAndValidate(body []byte) (*Envelope, []Error, []Warning, error) {
	return nil, nil, nil, errNotImplemented
}
