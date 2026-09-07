-- 自建部署初始化：单家庭行（02 §3.1；FAMILY_ID 环境变量须与此 id 一致）
INSERT INTO families (id, name) VALUES ('family-default', '默认家庭')
ON CONFLICT (id) DO NOTHING;
