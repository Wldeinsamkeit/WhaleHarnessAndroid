#!/bin/zsh

set -e
cd -- "$(dirname -- "$0")"

if ! command -v node >/dev/null 2>&1; then
  echo "未找到 Node.js，请先安装 Node.js 20 或更高版本。"
  read -r "?按回车键关闭…"
  exit 1
fi

if ! curl -fsS --max-time 2 http://127.0.0.1:3080 >/dev/null 2>&1; then
  echo "正在启动 DeepSeek Harness…"
  npx --yes @deepseek-ai/dsh web >"${TMPDIR:-/tmp}/whale-harness-dsh.log" 2>&1 &
  for _ in {1..45}; do
    curl -fsS --max-time 2 http://127.0.0.1:3080 >/dev/null 2>&1 && break
    sleep 1
  done
fi

if ! curl -fsS --max-time 2 http://127.0.0.1:3080 >/dev/null 2>&1; then
  echo "DeepSeek Harness 未能启动，请查看 ${TMPDIR:-/tmp}/whale-harness-dsh.log"
  read -r "?按回车键关闭…"
  exit 1
fi

exec node bridge.mjs
