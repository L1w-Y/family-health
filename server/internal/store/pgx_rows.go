package store

// pgx 行级读写与类型归一化：JSONMap ↔ PG 行。
// 列名全部来自 schema.go 白名单拼 SQL，业务值一律走参数绑定，无注入面。
import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgtype"

	"familyhealth/server/internal/model"
)

// querier 池与事务共用
type querier interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// rowTo JSONMap → 结构体（jsonb 归一化后可直接 JSON 往返）
func rowTo[T any](row model.JSONMap) (*T, error) {
	b, err := json.Marshal(row)
	if err != nil {
		return nil, err
	}
	var v T
	if err := json.Unmarshal(b, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

func getRowQ(ctx context.Context, q querier, table, familyID, id string) (model.JSONMap, error) {
	rows, err := queryRowsQ(ctx, q,
		fmt.Sprintf(`SELECT * FROM %s WHERE family_id=$1 AND id=$2`, table), familyID, id)
	if err != nil {
		return nil, err
	}
	if len(rows) == 0 {
		return nil, ErrNotFound
	}
	return rows[0], nil
}

// queryRowsQ 查询并归一化为 JSONMap（jsonb/日期/数值 → JSON 友好类型）
func queryRowsQ(ctx context.Context, q querier, sql string, args ...any) ([]model.JSONMap, error) {
	pgRows, err := q.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer pgRows.Close()
	var out []model.JSONMap
	for pgRows.Next() {
		vals, err := pgRows.Values()
		if err != nil {
			return nil, err
		}
		fields := pgRows.FieldDescriptions()
		row := model.JSONMap{}
		for i, f := range fields {
			row[string(f.Name)] = normalizeValue(vals[i])
		}
		out = append(out, row)
	}
	return out, pgRows.Err()
}

// normalizeValue PG 驱动类型 → JSON 可序列化类型
func normalizeValue(v any) any {
	switch t := v.(type) {
	case nil:
		return nil
	case time.Time:
		// date 列（ midnight UTC）→ YYYY-MM-DD；timestamptz/bigint 时刻列本就是 int64 不走这里
		if t.Hour() == 0 && t.Minute() == 0 && t.Second() == 0 && t.Nanosecond() == 0 {
			return t.Format("2006-01-02")
		}
		return t.UTC().UnixMilli()
	case []byte:
		// jsonb 列
		var v any
		if err := json.Unmarshal(t, &v); err != nil {
			return string(t)
		}
		return v
	case string:
		// jsonb 也可能以 string 返回
		if len(t) > 0 && (t[0] == '{' || t[0] == '[') {
			var v any
			if err := json.Unmarshal([]byte(t), &v); err == nil {
				return v
			}
		}
		return t
	case pgtype.Numeric:
		// numeric 列（glucose_mmol 等）→ float64 便于 JSON 输出
		if !t.Valid {
			return nil
		}
		f, err := t.Float64Value()
		if err != nil {
			return nil
		}
		return f
	default:
		return v
	}
}

// encodeCol 业务值 → PG 参数；jsonb 列序列化
func encodeCol(table, col string, v any) (any, error) {
	if v == nil {
		return nil, nil
	}
	if IsJSONBColumn(table, col) {
		b, err := json.Marshal(v)
		if err != nil {
			return nil, fmt.Errorf("store: jsonb 列 %s.%s 序列化失败: %w", table, col, err)
		}
		return string(b), nil
	}
	return v, nil
}

type pgxTx struct{ tx pgx.Tx }

func (t *pgxTx) NextSeq(ctx context.Context) (int64, error) {
	var seq int64
	err := t.tx.QueryRow(ctx, "SELECT nextval('"+SyncSequence+"')").Scan(&seq)
	if err != nil {
		return 0, fmt.Errorf("store: seq 取号失败: %w", err)
	}
	return seq, nil
}

func (t *pgxTx) InsertRow(ctx context.Context, table string, row model.JSONMap) error {
	cols := AllColumns(table)
	names := make([]string, 0, len(cols))
	ph := make([]string, 0, len(cols))
	args := make([]any, 0, len(cols))
	for i, col := range cols {
		v, err := encodeCol(table, col, row[col])
		if err != nil {
			return err
		}
		names = append(names, col)
		ph = append(ph, fmt.Sprintf("$%d", i+1))
		args = append(args, v)
	}
	_, err := t.tx.Exec(ctx,
		fmt.Sprintf(`INSERT INTO %s (%s) VALUES (%s)`, table,
			strings.Join(names, ", "), strings.Join(ph, ", ")), args...)
	return err
}

func (t *pgxTx) UpdateRow(ctx context.Context, table string, row model.JSONMap) error {
	// 整行 LWW（02 §4.3）：业务列全覆盖（未提供 → NULL），通用列仅刷新 updated_at/seq
	sets := []string{}
	args := []any{}
	n := 1
	for _, col := range BusinessColumns(table) {
		v, err := encodeCol(table, col, row[col])
		if err != nil {
			return err
		}
		sets = append(sets, fmt.Sprintf("%s=$%d", col, n))
		args = append(args, v)
		n++
	}
	sets = append(sets, fmt.Sprintf("updated_at=$%d", n))
	args = append(args, row["updated_at"])
	n++
	sets = append(sets, fmt.Sprintf("seq=$%d", n))
	args = append(args, row["seq"])
	n++
	args = append(args, row["family_id"], row["id"])
	tag, err := t.tx.Exec(ctx,
		fmt.Sprintf(`UPDATE %s SET %s WHERE family_id=$%d AND id=$%d`,
			table, strings.Join(sets, ", "), n, n+1), args...)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (t *pgxTx) GetRow(ctx context.Context, table, familyID, id string) (model.JSONMap, error) {
	return getRowQ(ctx, t.tx, table, familyID, id)
}

func (t *pgxTx) SoftDeleteRow(ctx context.Context, table, familyID, id string, seq, updatedAt int64) error {
	tag, err := t.tx.Exec(ctx,
		fmt.Sprintf(`UPDATE %s SET deleted=true, seq=$1, updated_at=$2 WHERE family_id=$3 AND id=$4`, table),
		seq, updatedAt, familyID, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (t *pgxTx) PutImportRecord(ctx context.Context, rec *ImportRecord) error {
	_, err := t.tx.Exec(ctx,
		`INSERT INTO import_records (import_id, family_id, device_id, payload_hash, response, created_at)
		 VALUES ($1,$2,$3,$4,$5,$6)`,
		rec.ImportID, rec.FamilyID, rec.DeviceID, rec.PayloadHash, rec.Response, rec.CreatedAt)
	if err != nil && isUniqueViolation(err) {
		return fmt.Errorf("store: import_id 冲突: %w", ErrNotFound)
	}
	return err
}

func (t *pgxTx) PutIdempotentResponse(ctx context.Context, deviceID, key string, resp []byte, createdAt int64) error {
	_, err := t.tx.Exec(ctx,
		`INSERT INTO idempotency_keys (device_id, key, response, created_at) VALUES ($1,$2,$3,$4)`,
		deviceID, key, resp, createdAt)
	if err != nil && isUniqueViolation(err) {
		return errors.New("store: Idempotency-Key 并发冲突")
	}
	return err
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}
