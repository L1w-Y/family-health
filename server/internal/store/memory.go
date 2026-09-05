package store

// MemoryStore 内存实现：供单元测试与本地开发，不依赖真实数据库。
// 行以 JSONMap 存储；事务用深拷贝快照回滚（仅测试规模数据，性能无关）。
import (
	"context"
	"encoding/json"
	"sort"
	"sync"

	"familyhealth/server/internal/model"
)

type MemoryStore struct {
	mu          sync.Mutex
	seq         int64
	families    map[string]model.Family
	devices     map[string]model.Device             // id → device
	rows        map[string]map[string]model.JSONMap // table → id → row
	imports     map[string]*ImportRecord            // familyID+"/"+importID
	idempotency map[string][]byte                   // deviceID+"/"+key
}

func NewMemoryStore() *MemoryStore {
	return &MemoryStore{
		families:    map[string]model.Family{},
		devices:     map[string]model.Device{},
		rows:        map[string]map[string]model.JSONMap{},
		imports:     map[string]*ImportRecord{},
		idempotency: map[string][]byte{},
	}
}

func (m *MemoryStore) Close() error { return nil }

// SeedFamily 测试辅助：预置家庭
func (m *MemoryStore) SeedFamily(f model.Family) { m.families[f.ID] = f }

func (m *MemoryStore) GetFamily(_ context.Context, id string) (*model.Family, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	f, ok := m.families[id]
	if !ok {
		return nil, ErrNotFound
	}
	return &f, nil
}

func (m *MemoryStore) CreateDevice(_ context.Context, d *model.Device) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.devices[d.ID] = *d
	return nil
}

func (m *MemoryStore) FindDeviceByTokenHash(_ context.Context, hash string) (*model.Device, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, d := range m.devices {
		if d.TokenHash == hash {
			cp := d
			return &cp, nil
		}
	}
	return nil, ErrNotFound
}

func (m *MemoryStore) TouchDevice(_ context.Context, id string, lastSeenAt int64) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	d, ok := m.devices[id]
	if !ok {
		return ErrNotFound
	}
	d.LastSeenAt = &lastSeenAt
	m.devices[id] = d
	return nil
}

// rowsOf 取表内全部行（深拷贝，避免调用方改穿快照）
func (m *MemoryStore) rowsOf(table, familyID string) []model.JSONMap {
	var out []model.JSONMap
	for _, r := range m.rows[table] {
		if str(r["family_id"]) == familyID {
			out = append(out, deepCopyRow(r))
		}
	}
	return out
}

func deepCopyRow(r model.JSONMap) model.JSONMap {
	b, _ := json.Marshal(r)
	var cp model.JSONMap
	_ = json.Unmarshal(b, &cp)
	return cp
}

func str(v any) string {
	s, _ := v.(string)
	return s
}

func boolOf(v any) bool {
	b, _ := v.(bool)
	return b
}

func rowAs[T any](r model.JSONMap) (*T, error) {
	b, err := json.Marshal(r)
	if err != nil {
		return nil, err
	}
	var v T
	if err := json.Unmarshal(b, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

func (m *MemoryStore) GetProfile(_ context.Context, familyID, id string) (*model.Profile, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r, ok := m.rows["profiles"][id]
	if !ok || str(r["family_id"]) != familyID || boolOf(r["deleted"]) {
		return nil, ErrNotFound
	}
	return rowAs[model.Profile](r)
}

func (m *MemoryStore) FindProfilesByName(_ context.Context, familyID, name string) ([]*model.Profile, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var out []*model.Profile
	for _, r := range m.rowsOf("profiles", familyID) {
		if str(r["name"]) == name && !boolOf(r["deleted"]) {
			p, err := rowAs[model.Profile](r)
			if err != nil {
				return nil, err
			}
			out = append(out, p)
		}
	}
	return out, nil
}

func (m *MemoryStore) GetMedicationItem(_ context.Context, familyID, id string) (*model.MedicationItem, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r, ok := m.rows["medication_items"][id]
	if !ok || str(r["family_id"]) != familyID || boolOf(r["deleted"]) {
		return nil, ErrNotFound
	}
	return rowAs[model.MedicationItem](r)
}

// FindActiveMedsByName 当前进行中条目（02 §3.9：end_date IS NULL AND deleted=false）精确名匹配
func (m *MemoryStore) FindActiveMedsByName(_ context.Context, familyID, profileID, name string) ([]*model.MedicationItem, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var out []*model.MedicationItem
	for _, r := range m.rowsOf("medication_items", familyID) {
		if str(r["profile_id"]) != profileID || str(r["name"]) != name || boolOf(r["deleted"]) {
			continue
		}
		if r["end_date"] != nil && str(r["end_date"]) != "" {
			continue
		}
		it, err := rowAs[model.MedicationItem](r)
		if err != nil {
			return nil, err
		}
		out = append(out, it)
	}
	return out, nil
}

func (m *MemoryStore) MissingAttachments(_ context.Context, familyID string, ids []string) ([]string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var missing []string
	for _, id := range ids {
		r, ok := m.rows["attachments"][id]
		if !ok || str(r["family_id"]) != familyID || boolOf(r["deleted"]) {
			missing = append(missing, id)
		}
	}
	return missing, nil
}

func (m *MemoryStore) GetRow(_ context.Context, table, familyID, id string) (model.JSONMap, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r, ok := m.rows[table][id]
	if !ok || str(r["family_id"]) != familyID {
		return nil, ErrNotFound
	}
	return deepCopyRow(r), nil
}

// ResolveCanonical item_name ∈ watch_items.aliases 精确匹配（02 §3.8 匹配规则）
func (m *MemoryStore) ResolveCanonical(_ context.Context, familyID, profileID, itemName string) (string, bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, r := range m.rowsOf("watch_items", familyID) {
		if str(r["profile_id"]) != profileID || boolOf(r["deleted"]) {
			continue
		}
		if str(r["canonical_name"]) == itemName {
			return itemName, true, nil
		}
		aliases, _ := r["aliases"].([]any)
		for _, a := range aliases {
			if str(a) == itemName {
				return str(r["canonical_name"]), true, nil
			}
		}
	}
	return "", false, nil
}

func (m *MemoryStore) GetImportRecord(_ context.Context, familyID, importID string) (*ImportRecord, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	rec, ok := m.imports[familyID+"/"+importID]
	if !ok {
		return nil, ErrNotFound
	}
	cp := *rec
	return &cp, nil
}

func (m *MemoryStore) GetIdempotentResponse(_ context.Context, deviceID, key string) ([]byte, bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	b, ok := m.idempotency[deviceID+"/"+key]
	return b, ok, nil
}

func (m *MemoryStore) ListChanges(_ context.Context, familyID string, since int64, limit int) ([]model.Change, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var out []model.Change
	for table := range m.rows {
		for _, r := range m.rowsOf(table, familyID) {
			seq, _ := r["seq"].(float64)
			if int64(seq) > since {
				out = append(out, model.Change{Table: table, Row: r})
			}
		}
	}
	sort.Slice(out, func(i, j int) bool {
		si, _ := out[i].Row["seq"].(float64)
		sj, _ := out[j].Row["seq"].(float64)
		return si < sj
	})
	if len(out) > limit {
		out = out[:limit]
	}
	return out, nil
}

// InTx 快照回滚式事务（测试规模）
func (m *MemoryStore) InTx(_ context.Context, fn func(tx Tx) error) error {
	m.mu.Lock()
	snapRows, _ := json.Marshal(m.rows)
	snapImports, _ := json.Marshal(m.imports)
	snapIdem, _ := json.Marshal(m.idempotency)
	snapSeq := m.seq
	m.mu.Unlock()

	tx := &memoryTx{m: m}
	if err := fn(tx); err != nil {
		m.mu.Lock()
		// 注意：json.Unmarshal 对 map 是合并语义，必须先重置再回填才能完整回滚
		m.rows = map[string]map[string]model.JSONMap{}
		m.imports = map[string]*ImportRecord{}
		m.idempotency = map[string][]byte{}
		_ = json.Unmarshal(snapRows, &m.rows)
		_ = json.Unmarshal(snapImports, &m.imports)
		_ = json.Unmarshal(snapIdem, &m.idempotency)
		m.seq = snapSeq
		m.mu.Unlock()
		return err
	}
	return nil
}

type memoryTx struct{ m *MemoryStore }

func (t *memoryTx) NextSeq(_ context.Context) (int64, error) {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	t.m.seq++
	return t.m.seq, nil
}

func (t *memoryTx) InsertRow(_ context.Context, table string, row model.JSONMap) error {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	if t.m.rows[table] == nil {
		t.m.rows[table] = map[string]model.JSONMap{}
	}
	t.m.rows[table][str(row["id"])] = deepCopyRow(row)
	return nil
}

func (t *memoryTx) UpdateRow(_ context.Context, table string, row model.JSONMap) error {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	tbl := t.m.rows[table]
	id := str(row["id"])
	existing, ok := tbl[id]
	if !ok {
		return ErrNotFound
	}
	// 整行 LWW（02 §4.3）：业务列全覆盖（缺省置 nil），通用列保留创建信息
	merged := model.JSONMap{
		"id":         existing["id"],
		"family_id":  existing["family_id"],
		"created_by": existing["created_by"],
		"source":     existing["source"],
		"created_at": existing["created_at"],
		"updated_at": row["updated_at"],
		"deleted":    existing["deleted"],
		"seq":        row["seq"],
	}
	for _, col := range BusinessColumns(table) {
		merged[col] = row[col] // 未提供 → nil（整行覆盖语义）
	}
	tbl[id] = merged
	return nil
}

func (t *memoryTx) GetRow(_ context.Context, table, familyID, id string) (model.JSONMap, error) {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	r, ok := t.m.rows[table][id]
	if !ok || str(r["family_id"]) != familyID {
		return nil, ErrNotFound
	}
	return deepCopyRow(r), nil
}

func (t *memoryTx) SoftDeleteRow(_ context.Context, table, familyID, id string, seq, updatedAt int64) error {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	r, ok := t.m.rows[table][id]
	if !ok || str(r["family_id"]) != familyID {
		return ErrNotFound
	}
	r["deleted"] = true
	r["seq"] = float64(seq)
	r["updated_at"] = float64(updatedAt)
	t.m.rows[table][id] = r
	return nil
}

func (t *memoryTx) PutImportRecord(_ context.Context, rec *ImportRecord) error {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	cp := *rec
	t.m.imports[rec.FamilyID+"/"+rec.ImportID] = &cp
	return nil
}

func (t *memoryTx) PutIdempotentResponse(_ context.Context, deviceID, key string, resp []byte, _ int64) error {
	t.m.mu.Lock()
	defer t.m.mu.Unlock()
	t.m.idempotency[deviceID+"/"+key] = resp
	return nil
}
