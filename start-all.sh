#!/usr/bin/env bash
# 药速达本地一键启动（Git Bash / Linux / macOS）
#
# 用法：
#   ./start-all.sh            # 复用已构建的 jar，启动全部服务
#   ./start-all.sh --build    # 先 mvn package，再启动
#   ./start-all.sh --no-ai    # 跳过 AI 服务（不依赖 Ollama / DeepSeek Key）
#
# 前置中间件：MySQL:3306、Redis(Stack):6379、Nacos:8848、RabbitMQ:5672、Ollama:18434
# 若本机已有同名 Docker 容器，脚本会尝试 docker start；否则请先按 README 启动。

set -uo pipefail
cd "$(dirname "$0")" || exit 1

BUILD=0
START_AI=1
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --no-ai) START_AI=0 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "未知参数：$arg（可用：--build / --no-ai）"; exit 1 ;;
  esac
done

JAVA_BIN="${JAVA_BIN:-java}"
LOG_DIR="$PWD/logs"
mkdir -p "$LOG_DIR"

# 载入本地配置（.env 已 gitignore）。变量已存在时不覆盖。
if [ -f .env ]; then
  while IFS= read -r line; do
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    case "$key" in *[!A-Za-z0-9_]*) continue ;; esac
    [ -z "${!key:-}" ] && export "$key=$val"
  done < .env
fi
export DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-sk-local-demo-no-key}"

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

wait_port() {
  local port=$1 label=$2 timeout=${3:-90} i=0
  while [ "$i" -lt "$timeout" ]; do
    port_open "$port" && { echo "  [OK]   $label :$port"; return 0; }
    sleep 1; i=$((i+1))
  done
  echo "  [FAIL] $label :$port 未在 ${timeout}s 内监听（见 logs/）"
  return 1
}

start_svc() {
  local name=$1 port=$2 jar=$3
  if port_open "$port"; then echo "  [SKIP] $name :$port 已在运行"; return 0; fi
  if [ ! -f "$jar" ]; then echo "  [MISS] $jar 不存在，请先执行 ./start-all.sh --build"; return 1; fi
  nohup "$JAVA_BIN" -jar "$jar" > "$LOG_DIR/$name.out.log" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
  echo "  [RUN]  $name :$port (pid $(cat "$LOG_DIR/$name.pid"))"
}

echo "== 1/3 检查中间件 =="
if ! port_open 3306; then docker start Study >/dev/null 2>&1; fi
if ! port_open 6379; then docker start my-redis >/dev/null 2>&1; fi
if ! port_open 8848; then docker start nacos >/dev/null 2>&1; fi
if ! port_open 5672; then docker start rabbitmq >/dev/null 2>&1; fi
if ! port_open 8718; then docker start sentinel-dashboard >/dev/null 2>&1; fi

INFRA_OK=1
for spec in "3306:MySQL" "6379:Redis" "8848:Nacos" "5672:RabbitMQ"; do
  port=${spec%%:*}; label=${spec#*:}
  if port_open "$port"; then echo "  [OK]   $label :$port"; else echo "  [WARN] $label :$port 未启动"; INFRA_OK=0; fi
done
port_open 18434 && echo "  [OK]   Ollama :18434" || echo "  [WARN] Ollama :18434 未启动（AI 的 RAG 依赖 bge-m3）"
port_open 8718 && echo "  [OK]   Sentinel :8718" || echo "  [WARN] Sentinel :8718 未启动（不影响业务）"
[ "$INFRA_OK" -eq 1 ] || echo "  ! 中间件缺失时服务会注册/连接失败，请先参考 README「快速启动」"

if [ "$BUILD" -eq 1 ]; then
  echo "== 2/3 构建（跳过测试）=="
  mvn -q clean package -DskipTests || { echo "构建失败，中止"; exit 1; }
else
  echo "== 2/3 复用已构建 jar（如需重构建：--build）=="
fi

echo "== 3/3 启动微服务 =="
start_svc ysd-user-service    8081 ysd-user-service/target/ysd-user-service-1.0-SNAPSHOT.jar
start_svc ysd-catalog-service 8084 ysd-catalog-service/target/ysd-catalog-service-1.0-SNAPSHOT.jar
start_svc ysd-order-service   8085 ysd-order-service/target/ysd-order-service-1.0-SNAPSHOT.jar
start_svc ysd-report-service  8083 ysd-report-service/target/ysd-report-service-1.0-SNAPSHOT.jar
start_svc ysd-admin-service   8082 ysd-admin-service/target/ysd-admin-service-1.0-SNAPSHOT.jar
start_svc ysd-push-service    8086 ysd-push-service/target/ysd-push-service-1.0-SNAPSHOT.jar
if [ "$START_AI" -eq 1 ]; then
  start_svc ysd-ai-service    8088 ysd-ai-service/target/ysd-ai-service-1.0-SNAPSHOT.jar
else
  echo "  [SKIP] ysd-ai-service（--no-ai）"
fi
start_svc ysd-gateway         9000 ysd-gateway/target/ysd-gateway-1.0-SNAPSHOT.jar

echo "-- 等待服务端口就绪（最多 120s）--"
FAIL=0
wait_port 8081 "user-service"    120 || FAIL=1
wait_port 8084 "catalog-service" 120 || FAIL=1
wait_port 8085 "order-service"   120 || FAIL=1
wait_port 8083 "report-service"  120 || FAIL=1
wait_port 8082 "admin-service"   120 || FAIL=1
wait_port 8086 "push-service"    120 || FAIL=1
[ "$START_AI" -eq 1 ] && { wait_port 8088 "ai-service" 120 || FAIL=1; }
wait_port 9000 "gateway"         120 || FAIL=1

echo
if [ "$FAIL" -eq 0 ]; then
  echo "全部服务已就绪：网关 http://localhost:9000 ，日志目录 logs/"
else
  echo "部分服务未就绪，请查看 logs/*.out.log；停止：./stop-all.sh"
fi
