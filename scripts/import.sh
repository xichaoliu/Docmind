#!/usr/bin/env bash
REPO="xichaoliu/docmind-rag"
PROJECT_NUMBER=1        # 上一步查到的看板编号
OWNER="@me"

# 先确保标签存在（已存在则更新，不报错）
for l in docs infra ai-python backend-java frontend; do
  gh label create "$l" --repo "$REPO" --force >/dev/null
done

while IFS='|' read -r label title; do
  [ -z "$title" ] && continue
  url=$(gh issue create --repo "$REPO" --title "$title" \
        --label "$label" --body "来自项目任务清单")
  gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$url"
  echo "已创建：$label $title"
done < tasks.txt
