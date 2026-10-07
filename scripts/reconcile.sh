#!/usr/bin/env bash
#
# 对账：比对【数据库 / gateway/uploads / rag/faiss_index】三处，找出孤儿数据。
#
# 设计原则
#   1. 数据库是唯一事实来源：document 表里存在的 docId 才算"有效文档"。
#   2. 默认只报告不删改；加 --apply 才执行。
#   3. --apply 只删磁盘上的孤儿目录，【绝不会删除数据库记录】。
#   4. 拿不到文档列表时直接中止 —— 否则空列表会让所有目录都像孤儿，一删全没。
#
# 用法
#   ./scripts/reconcile.sh            # 只报告（dry-run）
#   ./scripts/reconcile.sh --apply    # 执行清理
#   FORCE=1 ./scripts/reconcile.sh --apply   # 库确实是空的时候用
#
# 可用环境变量覆盖（都有默认值）
#   PSQL         psql 可执行文件路径（默认自动探测）
#   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
#   UPLOADS_DIR  默认 <repo>/gateway/uploads
#   FAISS_DIR    默认 <repo>/rag/faiss_index

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPLOADS_DIR="${UPLOADS_DIR:-$REPO_ROOT/gateway/uploads}"
FAISS_DIR="${FAISS_DIR:-$REPO_ROOT/rag/faiss_index}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-docmind}"
PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-postgres}}"

APPLY=0
[[ "${1:-}" == "--apply" ]] && APPLY=1
FORCE="${FORCE:-0}"

# ---------------------------------------------------------------- 工具函数

resolve_psql() {
  if [[ -n "${PSQL:-}" ]]; then printf '%s' "$PSQL"; return; fi
  if command -v psql >/dev/null 2>&1; then command -v psql; return; fi
  local app=/Applications/Postgres.app/Contents/Versions/latest/bin/psql
  if [[ -x "$app" ]]; then printf '%s' "$app"; return; fi
  printf ''
}

# 列出一个目录下的所有子目录名（没有则输出空）
list_dirs() {
  local base="$1" p
  [[ -d "$base" ]] || return 0
  for p in "$base"/*/; do
    [[ -d "$p" ]] || continue
    basename "$p"
  done
}

# 打印一个列表文件；为空时显示「（无）」
print_list() {
  local f="$1" n
  # 注意：grep -c 在计数为 0 时也会打印 "0" 并返回退出码 1，
  # 所以这里只能用 `|| true` 兜住退出码，不能再 echo 一个 0，
  # 否则 n 会变成两行的 "0\n0"，后面的 [[ -eq ]] 会报语法错误。
  n=$(grep -c . "$f" 2>/dev/null || true)
  [[ -n "$n" ]] || n=0
  if [[ "$n" -eq 0 ]]; then
    echo "  （无）"
  else
    sed 's/^/  /' "$f"
  fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------- 取三份列表

PSQL_BIN="$(resolve_psql)"
if [[ -z "$PSQL_BIN" ]]; then
  echo "找不到 psql。请安装 Postgres.app，或设置 PSQL=/path/to/psql" >&2
  exit 1
fi

if ! "$PSQL_BIN" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
      -tA -v ON_ERROR_STOP=1 \
      -c "SELECT doc_id FROM document" > "$TMP/db.raw" 2> "$TMP/db.err"; then
  echo "查询数据库失败，已中止（绝不会在拿不到文档列表的情况下清理文件）：" >&2
  sed 's/^/  /' "$TMP/db.err" >&2
  exit 1
fi

grep -v '^[[:space:]]*$' "$TMP/db.raw" 2>/dev/null | sort -u > "$TMP/db" || true
list_dirs "$UPLOADS_DIR" | sort -u > "$TMP/up"
list_dirs "$FAISS_DIR"   | sort -u > "$TMP/fa"

DB_N=$(grep -c . "$TMP/db" || true)
UP_N=$(grep -c . "$TMP/up" || true)
FA_N=$(grep -c . "$TMP/fa" || true)

# ---------------------------------------------------------------- 安全检查

if [[ "$DB_N" -eq 0 && $((UP_N + FA_N)) -gt 0 ]]; then
  echo "!! 数据库里没有任何文档，但磁盘上有 $((UP_N + FA_N)) 个目录。" >&2
  echo "!! 这通常意味着连错了库、表是空的、或者密码不对 —— 已中止，避免误删。" >&2
  echo "!! 如果确认这个库本来就是空的、磁盘目录确实要清，请加 FORCE=1 重跑。" >&2
  [[ "$FORCE" == "1" ]] || exit 1
fi

# 孤儿 = 磁盘上有、数据库里没有
comm -23 "$TMP/fa" "$TMP/db" > "$TMP/fa_orphan"
comm -23 "$TMP/up" "$TMP/db" > "$TMP/up_orphan"
# 缺失 = 数据库里有、磁盘上没有
comm -13 "$TMP/fa" "$TMP/db" > "$TMP/fa_missing"
comm -13 "$TMP/up" "$TMP/db" > "$TMP/up_missing"

# 状态为 READY 但没有索引 → 声称可检索，实际检索不到
if "$PSQL_BIN" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
     -tA -v ON_ERROR_STOP=1 \
     -c "SELECT doc_id FROM document WHERE status = 'READY'" 2>/dev/null \
     | grep -v '^[[:space:]]*$' | sort -u > "$TMP/ready"; then
  comm -23 "$TMP/ready" "$TMP/fa" > "$TMP/ready_no_index"
else
  : > "$TMP/ready_no_index"
fi

FA_ORPHAN_N=$(grep -c . "$TMP/fa_orphan" || true)
UP_ORPHAN_N=$(grep -c . "$TMP/up_orphan" || true)
FA_MISSING_N=$(grep -c . "$TMP/fa_missing" || true)
UP_MISSING_N=$(grep -c . "$TMP/up_missing" || true)
READY_NO_INDEX_N=$(grep -c . "$TMP/ready_no_index" || true)

# ---------------------------------------------------------------- 报告

# 注意：变量一律用 ${} 界定。写成 $VAR） 时，bash 会把全角右括号的首字节
# 当成变量名的一部分，配合 set -u 会直接报 unbound variable。
echo "=============================================="
echo " 对账报告"
echo "=============================================="
echo " 数据库文档 (事实来源) : ${DB_N}"
echo " uploads 目录           : ${UP_N}    (${UPLOADS_DIR})"
echo " faiss_index 目录       : ${FA_N}    (${FAISS_DIR})"
echo

echo "[高危] 索引孤儿 (${FA_ORPHAN_N})"
echo "  已删除文档的索引仍会被 load_store() 合并进检索结果，"
echo "  导致引用卡片指向数据库里不存在的文档。"
print_list "$TMP/fa_orphan"
echo

echo "[中] 上传文件孤儿 (${UP_ORPHAN_N})"
echo "  只占磁盘空间，不影响功能。"
print_list "$TMP/up_orphan"
echo

echo "[注意] 状态 READY 但没有索引 (${READY_NO_INDEX_N})"
echo "  这些文档声称已就绪，实际检索不到，需要重新入库。"
print_list "$TMP/ready_no_index"
echo

echo "[注意] 数据库有记录但没有索引 (${FA_MISSING_N})"
print_list "$TMP/fa_missing"
echo

echo "[注意] 数据库有记录但没有上传文件 (${UP_MISSING_N})"
echo "  索引还在、能检索，但无法重新入库。"
print_list "$TMP/up_missing"
echo

# ---------------------------------------------------------------- 清理

if [[ "$APPLY" -ne 1 ]]; then
  echo "=============================================="
  echo " dry-run：未做任何删改。加 --apply 执行清理。"
  echo "=============================================="
  exit 0
fi

echo "=============================================="
echo " 执行清理 (只删磁盘孤儿目录，不动数据库)"
echo "=============================================="

remove_orphans() {
  local base="$1" list="$2" id target
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    target="$base/$id"
    # 安全校验：最终路径必须确实落在 base 之下
    case "$target" in
      "$base"/*) ;;
      *) echo "  跳过可疑路径：$target" >&2; continue ;;
    esac
    if [[ -d "$target" ]]; then
      if rm -rf -- "$target"; then
        echo "  已删除 $target"
      else
        echo "  删除失败 $target" >&2
      fi
    fi
  done < "$list"
}

remove_orphans "$FAISS_DIR" "$TMP/fa_orphan"
remove_orphans "$UPLOADS_DIR" "$TMP/up_orphan"

echo
echo "清理完成。建议重新跑一次确认没有残留。"
