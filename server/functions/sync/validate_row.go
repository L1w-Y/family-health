package main

// 上行行级校验：枚举与必填的底线校验 + 用药写入路径规则（01 §5.2 规则 2/3，02 §3.9）。
// App 为可信客户端，此处只拦截明显非法；导入通道的完整校验在 internal/validate（03 契约）。
import (
	"fmt"
	"math"
	"net/http"

	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

func strOf(row model.JSONMap, key string) string {
	s, _ := row[key].(string)
	return s
}

func enumCheck(row model.JSONMap, key string, allowed ...string) error {
	v := strOf(row, key)
	if v == "" {
		return fmt.Errorf("%s 必填", key)
	}
	for _, a := range allowed {
		if v == a {
			return nil
		}
	}
	return fmt.Errorf("%s 枚举非法：%q", key, v)
}

// validateRowForWrite 写入前校验（isInsert：新增比更新多查必填）
func validateRowForWrite(r *http.Request, tx store.Tx, dev *model.Device, req *model.WriteRequest, isInsert bool) error {
	row := req.Row
	switch req.Table {
	case "profiles":
		if isInsert {
			if strOf(row, "name") == "" {
				return fmt.Errorf("profiles.name 必填")
			}
			if err := enumCheck(row, "gender", "male", "female", "unknown"); err != nil {
				return err
			}
		}
	case "measurements":
		if err := enumCheck(row, "type", "bp", "glucose"); err != nil {
			return err
		}
		if strOf(row, "profile_id") == "" {
			return fmt.Errorf("measurements.profile_id 必填")
		}
	case "medication_items":
		return validateMedicationItem(r, tx, dev, row, isInsert)
	case "daily_med_items":
		return validateDailyMedItem(r, tx, dev, row, isInsert)
	case "reminders":
		if err := enumCheck(row, "type", "medication", "measure", "checkup"); err != nil {
			return err
		}
	case "attachments":
		if v := strOf(row, "upload_state"); v != "" && v != "pending" && v != "done" {
			return fmt.Errorf("attachments.upload_state 枚举非法：%q", v)
		}
	case "notes":
		if isInsert && strOf(row, "text") == "" {
			return fmt.Errorf("notes.text 必填")
		}
		if n := len([]rune(strOf(row, "text"))); n > 500 {
			return fmt.Errorf("notes.text 超长（≤500 字，02 §3.14）")
		}
	}
	return nil
}

// validateMedicationItem 用药写入规则（01 §5.2 规则 2/3，02 §3.9）：
// 改量链：supersedes_id 必须指向本家庭同档案的既有条目；事由只落在 med_changes.note（条目不承载）。
func validateMedicationItem(r *http.Request, tx store.Tx, dev *model.Device, row model.JSONMap, isInsert bool) error {
	if isInsert {
		if strOf(row, "name") == "" {
			return fmt.Errorf("medication_items.name 必填")
		}
		if strOf(row, "profile_id") == "" {
			return fmt.Errorf("medication_items.profile_id 必填")
		}
		if err := enumCheck(row, "category", "long_term", "temporary"); err != nil {
			return err
		}
	}
	if v := strOf(row, "med_kind"); v != "" && v != "western" && v != "tcm" {
		return fmt.Errorf("medication_items.med_kind 枚举非法：%q", v)
	}
	rawDose, doseExists := row["dose_qty"]
	rawTimes, timesExists := row["dose_times_per_day"]
	hasDose := doseExists && rawDose != nil
	hasTimes := timesExists && rawTimes != nil
	hasUnit := strOf(row, "dose_unit") != ""
	if hasDose || hasUnit || hasTimes {
		if !hasDose || !hasUnit || !hasTimes {
			return fmt.Errorf("medication_items 结构化用量须同时提供 dose_qty、dose_unit、dose_times_per_day")
		}
		if strOf(row, "med_kind") == "tcm" {
			return fmt.Errorf("中药不使用结构化西药用量")
		}
		qty, ok := numberOf(rawDose)
		if !ok || qty <= 0 {
			return fmt.Errorf("medication_items.dose_qty 须为大于 0 的数值")
		}
		times, ok := numberOf(rawTimes)
		if !ok || times != math.Trunc(times) || times < 1 || times > 4 {
			return fmt.Errorf("medication_items.dose_times_per_day 须为 1–4 的整数")
		}
		slots, ok := stringListOf(row["dose_slots"])
		if !ok || len(slots) != int(times) {
			return fmt.Errorf("一天次数必须与 dose_slots 数量一致")
		}
		seen := map[string]bool{}
		for _, slot := range slots {
			if (slot != "morning" && slot != "noon" && slot != "evening" && slot != "bedtime") || seen[slot] {
				return fmt.Errorf("medication_items.dose_slots 含非法或重复时段")
			}
			seen[slot] = true
		}
	}
	if sup := strOf(row, "supersedes_id"); sup != "" {
		if !validate.IsUUID(sup) {
			return fmt.Errorf("supersedes_id 须为 UUID")
		}
		existing, err := tx.GetRow(r.Context(), "medication_items", dev.FamilyID, sup)
		if err != nil {
			return fmt.Errorf("改量链断裂：supersedes_id 指向的条目不存在（01 §5.2 规则 2）")
		}
		if strOf(existing, "profile_id") != strOf(row, "profile_id") {
			return fmt.Errorf("改量链必须同档案（01 §5.2 规则 2）")
		}
	}
	if ch := strOf(row, "change_id"); ch != "" {
		if !validate.IsUUID(ch) {
			return fmt.Errorf("change_id 须为 UUID")
		}
		if _, err := tx.GetRow(r.Context(), "med_changes", dev.FamilyID, ch); err != nil {
			return fmt.Errorf("批次归属失败：change_id 指向的用药变化不存在（01 §5.2 规则 3）")
		}
	}
	return nil
}

func validateDailyMedItem(r *http.Request, tx store.Tx, dev *model.Device, row model.JSONMap, _ bool) error {
	profileID := strOf(row, "profile_id")
	medicationID := strOf(row, "medication_item_id")
	if profileID == "" || medicationID == "" {
		return fmt.Errorf("daily_med_items.profile_id 与 medication_item_id 必填")
	}
	med, err := tx.GetRow(r.Context(), "medication_items", dev.FamilyID, medicationID)
	if err != nil || strOf(med, "profile_id") != profileID {
		return fmt.Errorf("daily_med_items.medication_item_id 必须指向同档案用药")
	}
	if dose, valid := numberOf(med["dose_qty"]); !valid || dose <= 0 || strOf(med, "dose_unit") == "" {
		return fmt.Errorf("daily_med_items 只能关联已填写结构化用量的用药")
	}
	allowedSlots := stringSetOf(med["dose_slots"])
	if times, valid := numberOf(med["dose_times_per_day"]); !valid || times != float64(len(allowedSlots)) {
		return fmt.Errorf("daily_med_items 关联用药的一天次数与服用时段不一致")
	}
	stocks, ok := row["stock_by_slot"].(map[string]any)
	if !ok {
		return fmt.Errorf("daily_med_items.stock_by_slot 须为对象")
	}
	for slot, raw := range stocks {
		if slot != "morning" && slot != "noon" && slot != "evening" && slot != "bedtime" {
			return fmt.Errorf("daily_med_items.stock_by_slot 时段非法：%q", slot)
		}
		if !allowedSlots[slot] {
			return fmt.Errorf("daily_med_items.stock_by_slot.%s 不属于该药的服药时段", slot)
		}
		qty, valid := numberOf(raw)
		if !valid || qty < 0 {
			return fmt.Errorf("daily_med_items.stock_by_slot.%s 须为非负数", slot)
		}
	}
	if countedAt, ok := numberOf(row["stock_counted_at"]); !ok || countedAt <= 0 {
		return fmt.Errorf("daily_med_items.stock_counted_at 须为有效时间戳")
	}
	return nil
}

func stringSetOf(value any) map[string]bool {
	result := map[string]bool{}
	switch values := value.(type) {
	case []any:
		for _, value := range values {
			if text, ok := value.(string); ok {
				result[text] = true
			}
		}
	case []string:
		for _, value := range values {
			result[value] = true
		}
	}
	return result
}

func stringListOf(value any) ([]string, bool) {
	switch values := value.(type) {
	case []any:
		result := make([]string, 0, len(values))
		for _, value := range values {
			text, ok := value.(string)
			if !ok {
				return nil, false
			}
			result = append(result, text)
		}
		return result, true
	case []string:
		return values, true
	default:
		return nil, false
	}
}

func numberOf(value any) (float64, bool) {
	switch n := value.(type) {
	case float64:
		return n, !math.IsNaN(n) && !math.IsInf(n, 0)
	case float32:
		return float64(n), true
	case int:
		return float64(n), true
	case int8:
		return float64(n), true
	case int16:
		return float64(n), true
	case int32:
		return float64(n), true
	case int64:
		return float64(n), true
	case uint:
		return float64(n), true
	case uint8:
		return float64(n), true
	case uint16:
		return float64(n), true
	case uint32:
		return float64(n), true
	case uint64:
		return float64(n), true
	default:
		return 0, false
	}
}
