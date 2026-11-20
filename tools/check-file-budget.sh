#!/usr/bin/env bash
# 文件体量闸门。
#
# 大文件只许变短：现在这些行数是既成事实，不是目标。新功能要么先进自己的区域文件，
# 要么先拆再写。要动某一行上限，唯一的合法方式是把它调低，并且代码同时变短。
#
# 用法：tools/check-file-budget.sh
# 退出码：超限返回 1，CI 直接红。
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
budget="$root/tools/file-budget.txt"
failed=0
stale=0

while IFS=$'\t' read -r path max || [ -n "$path" ]; do
  case "$path" in ''|'#'*) continue ;; esac
  file="$root/$path"
  if [ ! -f "$file" ]; then
    printf '::error file=%s::预算里的文件已经不存在，删掉这一行\n' "$path"
    failed=1
    continue
  fi
  lines=$(wc -l < "$file" | tr -d ' ')
  if [ "$lines" -gt "$max" ]; then
    printf '::error file=%s::%s 现有 %s 行，超过上限 %s 行。先把这一批写进区域文件，或者先拆再写\n' \
      "$path" "$path" "$lines" "$max"
    failed=1
  elif [ $((max - lines)) -gt 100 ]; then
    printf '::notice file=%s::%s 已降到 %s 行，可以把上限从 %s 调到 %s\n' \
      "$path" "$path" "$lines" "$max" "$lines"
    stale=1
  fi
done < "$budget"

if [ "$failed" -ne 0 ]; then
  echo "文件体量闸门未通过。"
  exit 1
fi
if [ "$stale" -ne 0 ]; then
  echo "文件体量闸门通过；上方标记的上限可以下调。"
else
  echo "文件体量闸门通过。"
fi
