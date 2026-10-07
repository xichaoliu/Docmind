from fastapi import HTTPException

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.document_service import ingest, delete_doc
import logging
logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal", tags=["documents"])


class IngestRequest(BaseModel):
    filePath: str
    docId: str
    userId: str | None = None
class IngestResponse(BaseModel):
    chunkCount: int


@router.post("/ingest")
def ingest_document(req: IngestRequest):
    try:
        logger.info("用户 userId=%s 正在入库 docId=%s", req.userId, req.docId)
        count = ingest(req.filePath, req.docId, req.userId)
        return IngestResponse(chunkCount=count)
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=404,
            detail=f"文件不存在: {req.filePath}"
        )
    except Exception as e:
        logger.error("入库失败：docId=%s", req.docId, exc_info=e)
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )


@router.delete("/documents/{doc_id}")
def delete_document(doc_id: str):
    delete_doc(doc_id)
    return {"docId": doc_id, "deleted": True}