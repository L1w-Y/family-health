#!/bin/sh
# 每日备份触发（宿主机 cron 调用；经容器内网命中 backup 服务，不暴露公网）
# 契约：docs/04-技术选型.md §2（每日 pg_dump，滚动 30 天）
cd /home/ubuntu/family-health/deploy || exit 1
docker compose exec -T backup sh -c \
  'wget -qO- --post-data "" --header "Authorization: Bearer $BACKUP_TOKEN" http://localhost:9000/backup' \
  >> /home/ubuntu/family-health/deploy/backups/trigger.log 2>&1
