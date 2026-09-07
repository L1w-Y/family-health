#!/bin/bash
# 服务器一键运维：安装备份 cron + 重置数据库为干净初始态（建表 + 家庭行）+ 重启服务
# 用法：bash server-reset.sh
set -e
cd /home/ubuntu/family-health/deploy

chmod +x backup-trigger.sh
mkdir -p backups

# 每日 03:30 触发备份（幂等安装；grep 空匹配需兜底）
crontab -l 2>/dev/null | grep -v backup-trigger > /tmp/fh_cron.tmp || true
echo "30 3 * * * /home/ubuntu/family-health/deploy/backup-trigger.sh" >> /tmp/fh_cron.tmp
crontab /tmp/fh_cron.tmp
rm -f /tmp/fh_cron.tmp
echo "== crontab =="
crontab -l

echo "== 重置数据库 =="
docker compose exec -T pg psql -U postgres -d familyhealth -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose exec -T pg psql -U postgres -d familyhealth -f /scripts/schema.sql | tail -1
docker compose exec -T pg psql -U postgres -d familyhealth -f /scripts/init-family.sql

echo "== 重启服务（EnsureSyncSeq 重建序列）=="
docker compose restart auth sync import backup
sleep 4
docker compose ps
