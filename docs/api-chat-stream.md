# 接口契约：POST /internal/chat/stream

流式问答接口，基于 SSE（Server-Sent Events）协议。前后端（Python ↔ Java ↔ React）均按本文档约定的字段和事件类型实现，中间层透传时不得更改字段名或结构。

## 基本信息

| 项 | 值 |
|---|---|
| 方法 | POST |
| 路径 | `/internal/chat/stream`（Python 内部接口；Java 对外暴露为 `/api/chat`） |
| 请求 Content-Type | `application/json` |
| 响应 Content-Type | `text/event-stream` |

## 请求

```json
{
  "question": "DocMind 用什么向量数据库？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| question | string | 是 | 用户提出的问题 |

> Java 层接入历史对话后，会在此基础上增加 `conversationId`、`history` 等字段，届时更新本文档。

## 响应格式（SSE）

响应体是一串以空行分隔的消息，每条消息包含 `event` 和 `data` 两行：

```
event: <事件名>
data: <JSON 字符串>

```

**注意**：每条消息结尾必须是两个换行符（`\n\n`），否则前端不会触发对应事件。`data` 内容统一使用 JSON 序列化，中文需保证 `ensure_ascii=False`（Python）或等价设置，避免转义成 `\uXXXX`。

## 事件类型

### 1. `token`

模型每生成一小段文字时触发，可能触发多次。

**触发时机**：生成过程中，逐块产出。

```
event: token
data: {"text": "根据"}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| text | string | 本次生成的文字片段（可能是几个字，不保证按词切分） |

前端处理：将 `text` 追加到当前正在显示的回答末尾，实现打字机效果。

---

### 2. `citations`

生成结束后触发一次，携带本次回答引用的资料来源。

**触发时机**：所有 `token` 事件发送完毕之后，`done` 之前。

```
event: citations
data: [
  {"docId": "doc-001", "chunkIndex": 0, "text": "DocMind 使用 FAISS 作为向量数据库..."},
  {"docId": "doc-001", "chunkIndex": 3, "text": "前端使用 React 和 TypeScript..."}
]
```

| 字段 | 类型 | 说明 |
|---|---|---|
| docId | string | 来源文档的 ID |
| chunkIndex | number | 该片段在文档中的分块序号 |
| text | string | 原文片段内容 |

前端处理：渲染成可展开的"引用来源"卡片，编号需与回答正文中的 `[1]`、`[2]` 标注一一对应（按数组顺序）。

---

### 3. `done`

标志本次生成完全结束，触发且仅触发一次。

**触发时机**：`citations` 事件之后，作为整个流程的结束标记。

```
event: done
data: {}
```

前端处理：停止显示"生成中"状态（如跳动圆点、光标闪烁），允许用户发起下一次提问或重新生成。

---

### 4. `error`

检索或生成过程中发生异常时触发，替代正常流程，触发后不再有后续的 `token`/`citations`/`done`。

**触发时机**：任意阶段出错时（知识库为空、模型调用失败等）。

```
event: error
data: {"message": "知识库为空，请先上传文档"}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| message | string | 面向用户可读的错误描述 |

前端处理：展示错误提示，提供"重试"操作，不进入生成中状态。

## 完整交互示例

一次正常问答的完整原始响应：

```
event: token
data: {"text": "根据"}

event: token
data: {"text": "资料"}

event: token
data: {"text": "，DocMind 使用 [1] FAISS 作为向量数据库。"}

event: citations
data: [{"docId": "doc-001", "chunkIndex": 0, "text": "DocMind 使用 FAISS 作为向量数据库，通过 Ollama 运行本地大模型..."}]

event: done
data: {}

```

一次出错的响应：

```
event: error
data: {"message": "知识库为空，请先上传文档"}

```

## 后端实现要点（Python / FastAPI）

```python
import json
from fastapi.responses import StreamingResponse

def to_sse(event: str, data) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

@app.post("/internal/chat/stream")
def chat_stream(req: ChatRequest):
    def gen():
        for event, data in answer_stream(req.question):
            yield to_sse(event, data)
    return StreamingResponse(
        gen(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
```

## 中间层透传要点（Java / Spring Boot）

- 原样转发事件流，不解析、不重组 `token` 内容（避免破坏流式效果）。
- 边转发边在服务端累积完整文本，`done` 事件触发后将完整回答和 `citations` 一并存入数据库。
- Python 服务不可用或超时时，Java 层需自行发出符合本契约格式的 `error` 事件，前端无需区分错误来自哪一层。

## 前端消费要点（React）

- 使用 `fetch` + `response.body.getReader()` 手动解析（`EventSource` 原生不支持 POST），或使用 `@microsoft/fetch-event-source` 库。
- 按空行（`\n\n`）切分消息，分别解析 `event:` 和 `data:` 两行。
- 网络层可能出现半条消息（TCP 分包），需要用缓冲区拼接后再按空行切分，不能假设一次 `read()` 恰好是完整的一条或多条消息。

## 验证方式

```bash
curl -N -X POST http://localhost:8000/internal/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question": "这份文档讲了什么？"}'
```

`-N` 禁用 curl 的输出缓冲，能看到事件逐条到达而非一次性打印，用于确认流式是否生效。

## 变更记录

| 日期 | 变更 |
|---|---|
| 初版 | 定义 token / citations / done / error 四种事件 |
