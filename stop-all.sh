#!/usr/bin/env bash
# 停止 start-all.sh 启动的微服务。加 --infra 同时停止本机 Docker 中间件。
set -uo pipefail
cd "$(dirname "$0")" || exit 1

STOP_INFRA=0
[ "${1:-}" = "--infra" ] && STOP_INFRA=1

for pidfile in logs/*.pid; do
  [ -f "$pidfile" ] || continue
  pid=$(cat "$pidfile")
  name=$(basename "$pidfile" .pid)
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || taskkill //PID "$pid" //F >/dev/null 2>&1
    echo "  [STOP] $name (pid $pid)"
  fi
  rm -f "$pidfile"
done

if [ "$STOP_INFRA" -eq 1 ]; then
  docker stop sentinel-dashboard rabbitmq nacos my-redis Study >/dev/null 2>&1 && \
    echo "  [STOP] Docker 中间件"
fi
echo "完成。"
