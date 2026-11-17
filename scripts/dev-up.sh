#!/usr/bin/env bash
# 本地开发服务管理：后台服务用新会话脱离终端，终端关掉 / 会话结束也不会被带走
#
# 用法：
#   scripts/dev-up.sh up            启动后端（jar 不存在时先打包），等健康检查通过
#   scripts/dev-up.sh up --build    强制重新打包后启动
#   scripts/dev-up.sh up --web      同时启动学员端（5173）与管理端（5174）开发服务
#   scripts/dev-up.sh down          停止后端
#   scripts/dev-up.sh down --web    同时停止两个前端开发服务
#   scripts/dev-up.sh restart       重启后端
#   scripts/dev-up.sh status        查看后端、前端、MySQL、Redis、对象存储状态
#   scripts/dev-up.sh logs [-f]     查看后端日志（-f 持续跟随）
#   scripts/dev-up.sh web-logs      查看前端开发服务日志
#
# 约定：
#   - 后端固定监听 8080（java -jar 不加 --server.port 时可能落到随机端口，前端代理会 502）
#   - PID 写 .run-logs/*.pid，日志写 .run-logs/*.log
#   - 启动时用 os.setsid 新建会话，进程组不随当前终端退出
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR="server/target/yunshu-zhixue-server-0.1.0-SNAPSHOT.jar"
JAVA_BIN="${JAVA_BIN:-java}"
PYTHON_BIN="${PYTHON_BIN:-$(command -v python3)}"
PORT="${SERVER_PORT:-8080}"
LOG_DIR="$ROOT/.run-logs"
LOG_FILE="$LOG_DIR/server.log"
PID_FILE="$LOG_DIR/server.pid"

mkdir -p "$LOG_DIR"

log() { printf '%s\n' "$*"; }

port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1
}

backend_pid() {
  if [ -f "$PID_FILE" ]; then
    local pid
    pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      printf '%s' "$pid"
      return 0
    fi
  fi
  port_pid "$PORT"
}

health() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true
}

wait_health() {
  local i code
  for i in $(seq 1 60); do
    code="$(health)"
    if [ "$code" = "200" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

build_jar() {
  log "▶ 打包后端（首次或 --build 时执行，约 30 秒）…"
  (cd server && mvn -q -DskipTests package)
}

start() {
  local force_build="${1:-}"
  local pid
  pid="$(backend_pid || true)"
  if [ -n "$pid" ]; then
    log "后端已在运行（PID ${pid}，端口 ${PORT}），无需重复启动。"
    return 0
  fi
  if [ ! -f "$JAR" ] || [ "$force_build" = "--build" ]; then
    build_jar
  fi

  log "▶ 启动后端（端口 ${PORT}）…"
  # os.setsid 新建会话：进程组与当前终端解耦，脚本退出后由 launchd 接管
  nohup "$PYTHON_BIN" -c 'import os,sys; os.setsid(); os.execvp(sys.argv[1], sys.argv[1:])' \
    "$JAVA_BIN" -jar "$JAR" --server.port="$PORT" >>"$LOG_FILE" 2>&1 </dev/null &
  echo $! >"$PID_FILE"

  if wait_health; then
    log "✔ 后端已启动：PID $(cat "$PID_FILE")，健康检查 http://127.0.0.1:$PORT/actuator/health 返回 200"
    log "  日志：${LOG_FILE}（scripts/dev-up.sh logs -f 可跟随）"
  else
    log "✘ 健康检查未通过，最近日志："
    tail -30 "$LOG_FILE"
    exit 1
  fi
}

stop() {
  local pid
  pid="$(backend_pid || true)"
  if [ -z "$pid" ]; then
    log "后端未在运行。"
    rm -f "$PID_FILE"
    return 0
  fi
  log "▶ 停止后端（PID ${pid}）…"
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  if kill -0 "$pid" 2>/dev/null; then
    log "  未在 10 秒内退出，强制结束"
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  log "✔ 已停止"
}

## 前端开发服务（学员端 5173、管理端 5174）
WEB_APPS=("student:5173:student" "admin:5174:admin")

start_web_app() {
  local name="$1" port="$2" dir="$3"
  local pid logf pidf
  pid="$(port_pid "$port" || true)"
  logf="$LOG_DIR/web-${name}.log"
  pidf="$LOG_DIR/web-${name}.pid"
  if [ -n "$pid" ]; then
    log "${name} 开发服务已在运行（端口 ${port}，PID ${pid}）"
    return 0
  fi
  log "▶ 启动 ${name} 开发服务（端口 ${port}）…"
  nohup "$PYTHON_BIN" -c 'import os,sys; os.setsid(); os.chdir(sys.argv[1]); os.execvp(sys.argv[2], sys.argv[2:])' \
    "$ROOT/web" pnpm "dev:${dir}" >>"$logf" 2>&1 </dev/null &
  echo $! >"$pidf"
  local i
  for i in $(seq 1 30); do
    if nc -z 127.0.0.1 "$port" 2>/dev/null; then
      log "✔ ${name} 已启动：http://127.0.0.1:${port}/"
      return 0
    fi
    sleep 1
  done
  log "✘ ${name} 未在 30 秒内就绪，日志：${logf}"
  return 1
}

stop_web() {
  local entry name port dir pid
  for entry in "${WEB_APPS[@]}"; do
    IFS=':' read -r name port dir <<<"$entry"
    pid="$(port_pid "$port" || true)"
    if [ -z "$pid" ]; then
      log "${name} 开发服务未在运行"
      rm -f "$LOG_DIR/web-${name}.pid"
      continue
    fi
    log "▶ 停止 ${name} 开发服务（端口 ${port}，PID ${pid}）…"
    kill "$pid" 2>/dev/null || true
    sleep 1
    kill -9 "$pid" 2>/dev/null || true
    rm -f "$LOG_DIR/web-${name}.pid"
  done
  log "✔ 前端开发服务已停止"
}

status() {
  local pid code
  pid="$(backend_pid || true)"
  if [ -n "$pid" ]; then
    code="$(health)"
    log "后端     运行中  PID $pid  端口 $PORT  健康检查 ${code:-无响应}"
  else
    log "后端     未运行"
  fi
  for entry in "学员端:5173" "管理端:5174" "MySQL:3306" "Redis:6379" "对象存储:8333"; do
    name="${entry%%:*}"
    p="${entry##*:}"
    if nc -z 127.0.0.1 "$p" 2>/dev/null; then
      log "$name 端口 $p 可连接"
    else
      log "$name 端口 $p 不可连接"
    fi
  done
}

case "${1:-status}" in
  up)
    WITH_WEB=0
    for arg in "${@:2}"; do
      case "$arg" in
        --web) WITH_WEB=1 ;;
      esac
    done
    start "${2:-}"
    if [ "$WITH_WEB" = "1" ]; then
      for entry in "${WEB_APPS[@]}"; do
        IFS=':' read -r name port dir <<<"$entry"
        start_web_app "$name" "$port" "$dir" || true
      done
    fi
    ;;
  down)
    stop
    if [ "${2:-}" = "--web" ]; then
      stop_web
    fi
    ;;
  restart)
    stop
    start "${2:-}"
    ;;
  status)
    status
    ;;
  logs)
    if [ "${2:-}" = "-f" ]; then
      tail -f "$LOG_FILE"
    else
      tail -n 80 "$LOG_FILE"
    fi
    ;;
  web-logs)
    tail -n 40 "$LOG_DIR"/web-*.log
    ;;
  build)
    build_jar
    ;;
  *)
    sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
