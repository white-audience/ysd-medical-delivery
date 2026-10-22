#!/usr/bin/env bash
# 导出可公开的开源仓库：基于审核后的文件清单生成全新 Git 历史。
#
# 不在原仓库执行提交、不改写历史；只读取工作区中已审核的文件。
# 默认排除授权未确认的 ysd-miniapp/；取得书面许可后可显式启用：
#   INCLUDE_MINIAPP=1 bash scripts/export-open-source.sh <输出目录>
#
# 用法：
#   bash scripts/export-open-source.sh [输出目录]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/../ysd-take-out-public}"
MANIFEST="${MANIFEST:-$ROOT/../ysd-take-out-public-manifest.txt}"
INCLUDE_MINIAPP="${INCLUDE_MINIAPP:-0}"

if [ -e "$OUT" ]; then
  echo "输出目录已存在：$OUT" >&2
  echo "请先移走或删除后再执行。" >&2
  exit 1
fi

# ---------------------------------------------------------------- 1. 审核清单
# 已跟踪文件全部参与审核；未跟踪文件只接受显式白名单，避免把本地材料带出。
UNTRACKED_ALLOW='^(\.github/|CODE_OF_CONDUCT\.md$|CONTRIBUTING\.md$|LICENSE$|NOTICE\.md$|SECURITY\.md$|scripts/|docker/nginx/|ysd-admin-web/)'
if [ "$INCLUDE_MINIAPP" = "1" ]; then
  UNTRACKED_ALLOW="$UNTRACKED_ALLOW|^ysd-miniapp/"
fi

{
  git -C "$ROOT" ls-files
  git -C "$ROOT" ls-files --others --exclude-standard | grep -E "$UNTRACKED_ALLOW" || true
} | sort -u > "$MANIFEST.raw"

# 排除目录与文件；.env.example 单独保留。
grep -vE \
  -e '^docs/(07-面试准备|09-项目记录|10-开源发布)/' \
  -e '^ysd-miniapp/' \
  -e '(^|/)(target|node_modules)/' \
  -e '(^|/)\.env($|\.)' \
  -e '^jmeter/\.local/' \
  -e '^jmeter\.log$' \
  -e '^logs/' \
  -e '(^|/)\.idea(\.bak[^/]*)?/' \
  -e '(^|/)(\.vscode|\.git|dist|build|coverage|\.cache|unpackage)/' \
  -e '(^|/)(backup|backups)/' \
  -e '\.(log|pid|tmp|bak|swp|zip|tar|tgz|gz|pem|key|jks|p12|pfx|dump)$' \
  "$MANIFEST.raw" > "$MANIFEST"
echo '.env.example' >> "$MANIFEST"
sort -u "$MANIFEST" -o "$MANIFEST"
rm -f "$MANIFEST.raw"

if [ "$INCLUDE_MINIAPP" = "1" ]; then
  # 启用小程序目录时重新并入（上面的统一排除规则默认会移除它）。
  git -C "$ROOT" ls-files --others --exclude-standard | grep -E '^ysd-miniapp/' >> "$MANIFEST" || true
  git -C "$ROOT" ls-files | grep -E '^ysd-miniapp/' >> "$MANIFEST" || true
  sort -u "$MANIFEST" -o "$MANIFEST"
fi

while IFS= read -r path; do
  if [ ! -f "$ROOT/$path" ]; then
    echo "清单中的文件不存在：$path" >&2
    exit 2
  fi
done < "$MANIFEST"

echo "[1/5] 审核清单：$(wc -l < "$MANIFEST") 个文件 -> $MANIFEST"

# ---------------------------------------------------------------- 2. 凭据阻断
# 命中只输出“文件:行号”，不输出真实凭据。
PATTERN='LTAI[0-9A-Za-z]{8,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}|PRIVATE KEY|appsecret[[:space:]]*[:=][[:space:]]*["'"'"']?[0-9a-f]{32}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.'
HITS=0
while IFS= read -r path; do
  [ "$path" = "scripts/export-open-source.sh" ] && continue
  if grep -I -qE "$PATTERN" "$ROOT/$path"; then
    grep -I -nE "$PATTERN" "$ROOT/$path" | sed -E 's/^([^:]+:[0-9]+):.*/\1/' | sed "s#^#$path:#"
    HITS=1
  fi
done < "$MANIFEST"
if [ "$HITS" -ne 0 ]; then
  echo "发现疑似真实凭据，已停止导出。请先处理并复核清单。" >&2
  exit 2
fi
echo "[2/5] 凭据扫描通过（未发现真实密钥；已脱敏示例除外）"

# ---------------------------------------------------------------- 3. 复制清单
mkdir -p "$OUT"
tar -C "$ROOT" -cf - -T "$MANIFEST" | tar -C "$OUT" -xf -
echo "[3/5] 已复制到独立目录：$OUT"

# ---------------------------------------------------------------- 4. 产物复核
if find "$OUT" \( -type d \( -name target -o -name node_modules -o -name .git \) \
  -o -type f \( -name '.env' -o -name '.env.local' -o -name '.env.development' \
  -o -name '.env.production' -o -name '.env.staging' -o -name '.env.uat' \
  -o -name '*.pem' -o -name '*.key' -o -name '*.jks' -o -name '*.p12' \) \) | grep -q .; then
  echo "产物中仍包含禁止文件，已停止。" >&2
  find "$OUT" \( -type d \( -name target -o -name node_modules -o -name .git \) \
    -o -type f \( -name '.env' -o -name '.env.local' -o -name '.env.development' \
    -o -name '.env.production' -o -name '.env.staging' -o -name '.env.uat' \
    -o -name '*.pem' -o -name '*.key' -o -name '*.jks' -o -name '*.p12' \) \) >&2
  exit 3
fi
if grep -qE '(^|/)docs/(07-面试准备|09-项目记录|10-开源发布)/' "$MANIFEST"; then
  echo "清单仍包含内部文档目录，已停止。" >&2
  exit 3
fi
echo "[4/5] 产物复核通过"

# ---------------------------------------------------------------- 5. 全新历史
cd "$OUT"
git init -q -b main
git config user.name "${PUBLIC_AUTHOR_NAME:-ysd-take-out contributors}"
git config user.email "${PUBLIC_AUTHOR_EMAIL:-noreply@ysd-take-out.invalid}"
git add -A
git commit -q -m "chore: initial open-source release"
echo "[5/5] 新历史：$(git log --oneline | wc -l) 个提交，分支 $(git branch --show-current)"

cat <<EOF

完成：$OUT
作者：$(git log -1 --format='%an <%ae>')

下一步（确认无误后由人工执行）：
  cd "$OUT"
  git remote add origin git@github.com:<你的账号>/ysd-take-out.git
  git push -u origin main

发布前务必：
  1. 在阿里云 RAM 撤销/轮换旧 OSS AccessKey，在微信后台重置 AppSecret；
  2. 不要与旧 Gitee 历史做 mirror / force-push；
  3. 在 GitHub 开启 Secret scanning 与 Dependabot。
EOF
