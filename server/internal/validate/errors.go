package validate

import "fmt"

// 错误码（03 §6.3 列出"常见 code"，本包在此基础上补充契约隐含场景的具体码）
const (
	CodeSchema           = "SCHEMA"            // 结构非法（JSON 语法、字段类型、日期格式等）
	CodeRequired         = "REQUIRED"          // 必填缺失
	CodeEnum             = "ENUM"              // 枚举值非法
	CodeDateFuture       = "DATE_FUTURE"       // 未来日期/时间
	CodeDateOrder        = "DATE_ORDER"        // 日期先后关系非法（03 §3 next_checkup_date 须晚于 checkup_date）
	CodeValueRange       = "VALUE_RANGE"       // 数值越出合法区间（03 §4 区间拦截）
	CodeTooLong          = "TOO_LONG"          // 字符串超长
	CodeProfileAmbiguous = "PROFILE_AMBIGUOUS" // 档案名不唯一
	CodeProfileNotFound  = "PROFILE_NOT_FOUND" // 档案不存在
	CodeMatchAmbiguous   = "MATCH_AMBIGUOUS"   // 用药 stop 匹配不唯一
	CodeMatchNotFound    = "MATCH_NOT_FOUND"   // 用药 stop 匹配不到进行中条目
	CodeAttachNotFound   = "ATTACHMENT_NOT_FOUND"
	CodeLimitExceeded    = "LIMIT_EXCEEDED" // 数量/体积超限（03 §6.4）
	// 警告码（03 §6.2）
	WarnBpSysLteDia = "BP_SYS_LTE_DIA"
)

// Error 校验错误（03 §6.3，阻断）
type Error struct {
	Path    string `json:"path"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Warning 非阻断警告（03 §6.2，入库并提示）
type Warning struct {
	Path    string `json:"path"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

type errors []Error

func (es *errors) add(path, code, msg string) {
	*es = append(*es, Error{Path: path, Code: code, Message: msg})
}

func (es *errors) addf(path, code, format string, args ...any) {
	*es = append(*es, Error{Path: path, Code: code, Message: fmt.Sprintf(format, args...)})
}
