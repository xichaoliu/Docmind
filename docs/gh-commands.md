# GitHub CLI (gh) 命令速查

按用途分类整理，方便直接查阅复用。

## 安装与登录

```bash
# 安装（macOS）
brew install gh

# 登录
gh auth login

# 补充 Project 操作权限
gh auth refresh -s project

# 确认当前登录账号
gh api user -q .login
gh auth status
```

## 仓库（repo）

```bash
# 列出自己能访问的仓库，确认准确的 owner/repo 路径
gh repo list --limit 20

# 创建仓库
gh repo create docmind-rag --public \
  --description "基于 RAG 的文档知识库问答：React + Spring Boot + FastAPI + Ollama" \
  --clone

# 设置默认仓库（设置后本目录下的 gh 命令可省略 --repo）
gh repo set-default xichaoliu/docmind-rag
```

## 标签（label）

```bash
# 查看所有标签
gh label list --repo xichaoliu/docmind-rag
gh label list --repo xichaoliu/docmind-rag --json name   # 查看原始字段，排查大小写/隐藏字符问题

# 新建/更新标签（--force 表示已存在则更新，不报错）
gh label create "ai-python" --repo xichaoliu/docmind-rag \
  --color "5319E7" --description "AI/Python 相关任务" --force

# 批量创建多个标签
for l in docs infra ai-python backend-java frontend; do
  gh label create "$l" --repo xichaoliu/docmind-rag --force
done

# 给标签改名（关联的 issue 会自动跟着改名，不用逐条改）
gh label edit api-python --repo xichaoliu/docmind-rag --name "ai-python"

# 删除标签（不影响已打过该标签的 issue 本身，只是去掉标记）
gh label delete api-python --repo xichaoliu/docmind-rag --yes
```

## Issue

```bash
# 创建 issue
gh issue create --repo xichaoliu/docmind-rag \
  --title "任务标题" \
  --label ai-python \
  --body-file plan.md
  # 或直接 --body "正文内容"

# 查看 issue 列表
gh issue list --repo xichaoliu/docmind-rag
gh issue list --repo xichaoliu/docmind-rag --label ai-python
gh issue list --repo xichaoliu/docmind-rag --search "Docker in:title"

# 只取编号，方便批量处理
gh issue list --repo xichaoliu/docmind-rag --label api-python --json number -q '.[].number'

# 编辑 issue 的标签
gh issue edit 编号 --repo xichaoliu/docmind-rag --add-label ai-python
gh issue edit 编号 --repo xichaoliu/docmind-rag --remove-label docs --add-label infra

# 关闭 issue
gh issue close 编号 --repo xichaoliu/docmind-rag
```

### 批量迁移标签（把打了旧标签的 issue 都换成新标签）

```bash
gh issue list --repo xichaoliu/docmind-rag --label api-python --json number -q '.[].number' | \
  xargs -I{} gh issue edit {} --repo xichaoliu/docmind-rag \
  --remove-label api-python --add-label ai-python
```

## Project（看板）

```bash
# 列出自己的看板，获取 Project 编号
gh project list --owner @me

# 把某个 issue/PR 加进看板
gh project item-add 看板编号 --owner @me --url <issue或PR的URL>
```

## PR（Pull Request）

```bash
# 创建 PR
gh pr create --title "PR 标题" --body "改了什么、为什么" --base main

# 合并方式建议在网页上操作：选择 Squash and merge
```

## 批量导入脚本（`scripts/import.sh`）

用于从 `tasks.txt` 批量建 Issue 并加入看板：

```bash
#!/usr/bin/env bash
REPO="xichaoliu/docmind-rag"
PROJECT_NUMBER=1
OWNER="@me"

for l in docs infra ai-python backend-java frontend; do
  gh label create "$l" --repo "$REPO" --force >/dev/null
done

while IFS='|' read -r label title; do
  [ -z "$title" ] && continue
  url=$(gh issue create --repo "$REPO" --title "$title" \
        --label "$label" --body "来自项目任务清单")
  gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$url"
  echo "已创建：$title"
done < tasks.txt
```

运行：

```bash
chmod +x scripts/import.sh && ./scripts/import.sh
```

`tasks.txt` 格式（每行 `标签|标题`）：

```
ai-python|拆分 demo：ingest/load_store/answer_chain
frontend|封装 useChatStream Hook
```

## 踩过的坑（备注，避免再犯）

1. **用户名拼写混淆**：`xichaoliu`（本地电脑用户名）和实际 GitHub 用户名容易搞混/打错，导致 `--repo` 参数错误、连续 404。用 `gh api user -q .login` 或 `gh repo list` 确认准确路径，不要手打。
2. **命令续行符 `\` 后不能有多余空格**，否则续行失效，报 "too many arguments"。建议写成一行，或用 `gh repo set-default` 省略 `--repo` 参数减少出错概率。
3. **标签改名时，如果目标名已存在会报 "already exists"**，不能用 `edit --name`，要用批量迁移 + 删除旧标签的方式（见上面"批量迁移标签"部分）。
