package validate

import "errors"

var errNotImplemented = errors.New("validate: 未实现（M1 交付）")

// 常见错误码（03 §6.3）
const (
	CodeSchema           = "SCHEMA"
	CodeRequired         = "REQUIRED"
	CodeEnum             = "ENUM"
	CodeDateFuture       = "DATE_FUTURE"
	CodeProfileAmbiguous = "PROFILE_AMBIGUOUS"
	CodeProfileNotFound  = "PROFILE_NOT_FOUND"
	CodeMatchAmbiguous   = "MATCH_AMBIGUOUS"
	CodeAttachNotFound   = "ATTACHMENT_NOT_FOUND"
	CodeLimitExceeded    = "LIMIT_EXCEEDED"
)
