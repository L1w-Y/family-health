package store

// PGXStore 生产实现：pgx/v5 连接托管 PostgreSQL。
// 契约：docs/04-技术选型.md §4（云函数经环境内网直连，DATABASE_URL 注入；seq 用 PG 序列）
import (
	"context"
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"familyhealth/server/internal/model"
)

// SyncSequence 全局 seq 序列名（02 §4.1；04 §4.2）
const SyncSequence = "sync_seq"

type PGXStore struct {
	pool *pgxpool.Pool
}

// Connect 按环境变量 DATABASE_URL 建立连接池（云函数单并发：小池）。
// 同时确保 sync_seq 序列存在（幂等）。
func Connect(ctx context.Context) (*PGXStore, error) {
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		return nil, errors.New("store: DATABASE_URL 未配置")
	}
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("store: DATABASE_URL 解析失败: %w", err)
	}
	cfg.MaxConns = 2 // 云函数实例级并发极低
	cfg.MinConns = 0
	cfg.MaxConnIdleTime = 5 * time.Minute
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("store: 连接失败: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("store: Ping 失败: %w", err)
	}
	s := &PGXStore{pool: pool}
	if err := s.EnsureSyncSeq(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return s, nil
}

// EnsureSyncSeq CREATE SEQUENCE IF NOT EXISTS（04 §4.2：每次写入在事务内取号赋值）
func (s *PGXStore) EnsureSyncSeq(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, "CREATE SEQUENCE IF NOT EXISTS "+SyncSequence)
	if err != nil {
		return fmt.Errorf("store: 创建序列失败: %w", err)
	}
	return nil
}

func (s *PGXStore) Close() error {
	s.pool.Close()
	return nil
}

func (s *PGXStore) GetFamily(ctx context.Context, id string) (*model.Family, error) {
	var f model.Family
	err := s.pool.QueryRow(ctx, `SELECT id, name FROM families WHERE id=$1`, id).Scan(&f.ID, &f.Name)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &f, nil
}

func (s *PGXStore) CreateDevice(ctx context.Context, d *model.Device) error {
	_, err := s.pool.Exec(ctx,
		`INSERT INTO devices (id, family_id, created_by, source, created_at, updated_at, deleted, seq,
		   type, display_name, token_hash, revoked_at, last_seen_at)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
		d.ID, d.FamilyID, d.CreatedBy, string(d.Source), d.CreatedAt, d.UpdatedAt, d.Deleted, d.Seq,
		string(d.Type), d.DisplayName, d.TokenHash, d.RevokedAt, d.LastSeenAt)
	return err
}

func (s *PGXStore) FindDeviceByTokenHash(ctx context.Context, hash string) (*model.Device, error) {
	var d model.Device
	err := s.pool.QueryRow(ctx,
		`SELECT id, family_id, created_by, source, created_at, updated_at, deleted, seq,
		   type, display_name, token_hash, revoked_at, last_seen_at
		 FROM devices WHERE token_hash=$1`, hash).
		Scan(&d.ID, &d.FamilyID, &d.CreatedBy, &d.Source, &d.CreatedAt, &d.UpdatedAt, &d.Deleted, &d.Seq,
			&d.Type, &d.DisplayName, &d.TokenHash, &d.RevokedAt, &d.LastSeenAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &d, nil
}

func (s *PGXStore) TouchDevice(ctx context.Context, id string, lastSeenAt int64) error {
	_, err := s.pool.Exec(ctx, `UPDATE devices SET last_seen_at=$2 WHERE id=$1`, id, lastSeenAt)
	return err
}

func (s *PGXStore) GetProfile(ctx context.Context, familyID, id string) (*model.Profile, error) {
	row, err := getRowQ(ctx, s.pool, "profiles", familyID, id)
	if err != nil {
		return nil, err
	}
	if del, _ := row["deleted"].(bool); del {
		return nil, ErrNotFound
	}
	return rowTo[model.Profile](row)
}

func (s *PGXStore) FindProfilesByName(ctx context.Context, familyID, name string) ([]*model.Profile, error) {
	rows, err := queryRowsQ(ctx, s.pool,
		`SELECT * FROM profiles WHERE family_id=$1 AND name=$2 AND deleted=false`, familyID, name)
	if err != nil {
		return nil, err
	}
	out := make([]*model.Profile, 0, len(rows))
	for _, r := range rows {
		p, err := rowTo[model.Profile](r)
		if err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, nil
}

func (s *PGXStore) GetMedicationItem(ctx context.Context, familyID, id string) (*model.MedicationItem, error) {
	row, err := getRowQ(ctx, s.pool, "medication_items", familyID, id)
	if err != nil {
		return nil, err
	}
	if del, _ := row["deleted"].(bool); del {
		return nil, ErrNotFound
	}
	return rowTo[model.MedicationItem](row)
}

func (s *PGXStore) FindActiveMedsByName(ctx context.Context, familyID, profileID, name string) ([]*model.MedicationItem, error) {
	rows, err := queryRowsQ(ctx, s.pool,
		`SELECT * FROM medication_items
		 WHERE family_id=$1 AND profile_id=$2 AND name=$3 AND end_date IS NULL AND deleted=false`,
		familyID, profileID, name)
	if err != nil {
		return nil, err
	}
	out := make([]*model.MedicationItem, 0, len(rows))
	for _, r := range rows {
		it, err := rowTo[model.MedicationItem](r)
		if err != nil {
			return nil, err
		}
		out = append(out, it)
	}
	return out, nil
}

func (s *PGXStore) MissingAttachments(ctx context.Context, familyID string, ids []string) ([]string, error) {
	var missing []string
	for _, id := range ids {
		var n int
		err := s.pool.QueryRow(ctx,
			`SELECT count(1) FROM attachments WHERE family_id=$1 AND id=$2 AND deleted=false`,
			familyID, id).Scan(&n)
		if err != nil {
			return nil, err
		}
		if n == 0 {
			missing = append(missing, id)
		}
	}
	return missing, nil
}

func (s *PGXStore) GetRow(ctx context.Context, table, familyID, id string) (model.JSONMap, error) {
	return getRowQ(ctx, s.pool, table, familyID, id)
}

func (s *PGXStore) ResolveCanonical(ctx context.Context, familyID, profileID, itemName string) (string, bool, error) {
	// canonical_name 自身命中，或 item_name ∈ aliases（02 §3.8 精确匹配）
	var canonical string
	err := s.pool.QueryRow(ctx,
		`SELECT canonical_name FROM watch_items
		 WHERE family_id=$1 AND profile_id=$2 AND deleted=false
		   AND (canonical_name=$3 OR aliases @> to_jsonb(ARRAY[$3]::text[]))
		 LIMIT 1`, familyID, profileID, itemName).Scan(&canonical)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return canonical, true, nil
}

func (s *PGXStore) GetImportRecord(ctx context.Context, familyID, importID string) (*ImportRecord, error) {
	var rec ImportRecord
	err := s.pool.QueryRow(ctx,
		`SELECT import_id, family_id, device_id, payload_hash, response, created_at
		 FROM import_records WHERE family_id=$1 AND import_id=$2`, familyID, importID).
		Scan(&rec.ImportID, &rec.FamilyID, &rec.DeviceID, &rec.PayloadHash, &rec.Response, &rec.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &rec, nil
}

func (s *PGXStore) GetIdempotentResponse(ctx context.Context, deviceID, key string) ([]byte, bool, error) {
	var resp []byte
	err := s.pool.QueryRow(ctx,
		`SELECT response FROM idempotency_keys WHERE device_id=$1 AND key=$2`, deviceID, key).Scan(&resp)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	return resp, true, nil
}

func (s *PGXStore) ListChanges(ctx context.Context, familyID string, since int64, limit int) ([]model.Change, error) {
	var out []model.Change
	for table := range tables {
		rows, err := queryRowsQ(ctx, s.pool,
			fmt.Sprintf(`SELECT * FROM %s WHERE family_id=$1 AND seq>$2 ORDER BY seq ASC LIMIT $3`, table),
			familyID, since, limit)
		if err != nil {
			return nil, err
		}
		for _, r := range rows {
			out = append(out, model.Change{Table: table, Row: r})
		}
	}
	sortChangesBySeq(out)
	if len(out) > limit {
		out = out[:limit]
	}
	return out, nil
}

// InTx 单事务执行（04 §4.3：导入批次原子，失败即回滚）
func (s *PGXStore) InTx(ctx context.Context, fn func(tx Tx) error) error {
	pgTx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = pgTx.Rollback(ctx) }()
	if err := fn(&pgxTx{tx: pgTx}); err != nil {
		return err
	}
	return pgTx.Commit(ctx)
}

// sortChangesBySeq 稳定升序（seq 由 DB 序列保证全局递增）
func sortChangesBySeq(cs []model.Change) {
	// 小批量直接插入排序，避免引包
	for i := 1; i < len(cs); i++ {
		for j := i; j > 0 && seqOf(cs[j-1].Row) > seqOf(cs[j].Row); j-- {
			cs[j-1], cs[j] = cs[j], cs[j-1]
		}
	}
}

func seqOf(r model.JSONMap) int64 {
	switch v := r["seq"].(type) {
	case int64:
		return v
	case float64:
		return int64(v)
	case int:
		return int64(v)
	}
	return 0
}
