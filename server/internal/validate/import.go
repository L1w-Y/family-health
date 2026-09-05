package validate

// ParseAndValidate：03 §1 原则 1（整体原子，任一字段非法整体拒绝）的入口。
// 校验规则逐条对应 03 §2~§6.4，测试见同包 *_test.go。
import (
	"bytes"
	"encoding/json"
)

// ParseAndValidate 解析并整体校验导入文本。
// 返回：解析后的信封（校验失败时也可能非 nil，供调试）、阻断错误、非阻断警告。
// error 仅用于调用方无法继续的意外；当前实现恒为 nil——所有非法输入都归入 []Error。
func ParseAndValidate(body []byte) (*Envelope, []Error, []Warning, error) {
	var es errors
	var ws []Warning

	if len(body) == 0 {
		es.add("", CodeRequired, "请求体为空")
		return nil, es, nil, nil
	}
	if len(body) > MaxBodyBytes {
		es.addf("", CodeLimitExceeded, "单请求体积超限（≤%dKB）", MaxBodyBytes/1024)
		return nil, es, nil, nil
	}

	var env Envelope
	dec := json.NewDecoder(bytes.NewReader(body))
	if err := dec.Decode(&env); err != nil {
		es.add("", CodeSchema, "JSON 解析失败："+err.Error())
		return nil, es, nil, nil
	}
	// 信封后不允许拼接第二个 JSON 值
	if dec.More() {
		es.add("", CodeSchema, "JSON 尾部存在多余内容")
		return nil, es, nil, nil
	}

	checkEnvelope(&env, &es)
	switch env.Payload.Type {
	case PayloadCheckupEvent:
		checkEventPayload(env.Payload.Event, &es)
	case PayloadMeasurements:
		checkMeasurements(env.Payload.Items, &es, &ws)
	case PayloadMedChanges:
		checkMedChanges(env.Payload.ChangeSet, &es)
	case "":
		// checkEnvelope 已报 REQUIRED
	default:
		// checkEnvelope 已报 ENUM
	}
	return &env, es, ws, nil
}

// checkEnvelope 信封字段（03 §2）
func checkEnvelope(env *Envelope, es *errors) {
	if env.Format == "" {
		es.add("format", CodeRequired, "format 必填")
	} else if env.Format != FormatName {
		es.addf("format", CodeSchema, "format 固定为 %q", FormatName)
	}
	if env.Version == 0 {
		es.add("version", CodeRequired, "version 必填")
	} else if env.Version != FormatVersion {
		es.addf("version", CodeSchema, "version 一期固定为 %d", FormatVersion)
	}
	if env.ImportID == "" {
		es.add("import_id", CodeRequired, "import_id 必填")
	} else if !IsUUID(env.ImportID) {
		es.add("import_id", CodeSchema, "import_id 须为 UUID")
	}
	if env.Profile.ID == "" && env.Profile.Name == "" {
		es.add("profile_ref", CodeRequired, "profile_ref 必填（id 或 name 二选一）")
	} else if !oneOf(env.Profile.ID, env.Profile.Name) {
		es.add("profile_ref", CodeSchema, "profile_ref 的 id 与 name 只能二选一")
	} else if env.Profile.ID != "" && !IsUUID(env.Profile.ID) {
		es.add("profile_ref.id", CodeSchema, "profile_ref.id 须为 UUID")
	}
	if env.Payload.Type == "" {
		es.add("payload.type", CodeRequired, "payload.type 必填")
		return
	}
	switch env.Payload.Type {
	case PayloadCheckupEvent, PayloadMeasurements, PayloadMedChanges:
	default:
		es.addf("payload.type", CodeEnum, "payload.type 须为 %s / %s / %s 之一",
			PayloadCheckupEvent, PayloadMeasurements, PayloadMedChanges)
	}
}
