"""FastAPI HTTP 接口。"""
import json
from typing import Literal

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from app.services.chat_service import answer_stream
import logging
logger = logging.getLogger(__name__)
router = APIRouter(prefix="/internal", tags=["chat"])


class HistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str
class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=16_000)
    history: list[HistoryMessage] = [] 
    docIds: list[str] | None = None
    userId: str | None = None


class ChatResponse(BaseModel):
    answer: str

def to_sse(event: str, data) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/chat/stream")
def chat_stream(req: ChatRequest):
    logger.info("用户 userId=%s 正在向文档 docids=%s 提问 question=%s", req.userId, req.docIds, req.question)
    def gen():
        if req.docIds is not None and len(req.docIds) == 0:
            # yield ("error", {"message": "没有可检索的文档"})
            yield to_sse("token", {"text": "没有可检索的文档。"})
            yield to_sse("done", {})
            return
        for event, data in answer_stream(req.question, req.history, req.docIds, req.userId):
            yield to_sse(event, data)
    return StreamingResponse(
            gen(), 
            media_type="text/event-stream",        
            headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",   # 避免 Nginx 之类的代理缓冲导致不实时
        })
