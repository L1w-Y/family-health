package validate

// 通用字段级校验助手（03 §2~§5 共用）
import (
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

var uuidRe = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// IsUUID 校验 UUID 文本形态（不校验版本位）
func IsUUID(s string) bool { return uuidRe.MatchString(s) }

// now 可注入，便于测试未来日期规则；默认 time.Now
var now = time.Now

// parseDate 严格解析 ISO YYYY-MM-DD（03 全文日历日期不带时区，02 §1 时间存储决策）
func parseDate(s string) (time.Time, bool) {
	if len(s) != 10 {
		return time.Time{}, false
	}
	t, err := time.ParseInLocation("2006-01-02", s, time.UTC)
	if err != nil || t.Format("2006-01-02") != s {
		return time.Time{}, false
	}
	return t, true
}

// checkDate 校验日历日期字段；empty 视为缺失由调用方按必填与否处理
func checkDate(es *errors, path, s string) (time.Time, bool) {
	t, ok := parseDate(s)
	if !ok {
		es.add(path, CodeSchema, "日期须为 YYYY-MM-DD 且为真实存在的日期")
	}
	return t, ok
}

// checkNotFuture 日历日期不允许未来（03 §3 容忍 +1 天时区误差）
func checkNotFuture(es *errors, path string, t time.Time) {
	today := now().UTC().Truncate(24 * time.Hour)
	if t.After(today.Add(24 * time.Hour)) {
		es.add(path, CodeDateFuture, "不允许未来日期")
	}
}

// parseISOTime 解析 ISO8601 带偏移时刻（03 §4 measured_at），返回 UTC 时刻与偏移分钟
func parseISOTime(s string) (time.Time, int, bool) {
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		return time.Time{}, 0, false
	}
	_, offSec := t.Zone()
	return t.UTC(), offSec / 60, true
}

// checkLen 字符串长度上限（按字符数计，契约"≤50 字"为字数语义）
func checkLen(es *errors, path, s string, max int, field string) {
	if utf8.RuneCountInString(s) > max {
		es.addf(path, CodeTooLong, "%s 超长（≤%d 字）", field, max)
	}
}

// checkIntRange 整数值合法区间（03 §4）；非整数报 SCHEMA
func checkIntRange(es *errors, path string, v *float64, min, max float64, field string) (int, bool) {
	if v == nil {
		return 0, false
	}
	if *v != float64(int(*v)) {
		es.addf(path, CodeSchema, "%s 须为整数", field)
		return 0, false
	}
	if *v < min || *v > max {
		es.addf(path, CodeValueRange, "%s 越出合法区间（%.0f~%.0f），请核对是否手误", field, min, max)
		return 0, false
	}
	return int(*v), true
}

// checkNumRange 小数值合法区间（03 §4）
func checkNumRange(es *errors, path string, v *float64, min, max float64, field string) bool {
	if v == nil {
		return false
	}
	if *v < min || *v > max {
		es.addf(path, CodeValueRange, "%s 越出合法区间（%g~%g），请核对是否手误", field, min, max)
		return false
	}
	return true
}

// numericString 字符串可无损解析为数字时返回数值（03 §3.2 防 AI 类型抖动）
func numericString(s string) (float64, bool) {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0, false
	}
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return 0, false
	}
	// 排除 "NaN"/"Inf" 等非有限数
	if f != f || f > 1e308 || f < -1e308 {
		return 0, false
	}
	return f, true
}

// oneOf 二选一校验：恰好一个非空
func oneOf(a, b string) bool {
	return (a == "") != (b == "")
}
