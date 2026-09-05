package main

// 上行行级校验：枚举与必填的底线校验 + 用药写入路径规则（01 §5.2 规则 2/3，02 §3.9）。
// App 为可信客户端，此处只拦截明显非法；导入通道的完整校验在 internal/validate（03 契约）。
import (
	"fmt"
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
