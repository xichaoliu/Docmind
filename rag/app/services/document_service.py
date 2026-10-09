"""入库、加载索引、问答链。。"""
import shutil
from pathlib import Path

from langchain_community.document_loaders import TextLoader, PyPDFLoader, Docx2txtLoader
from langchain_community.vectorstores import FAISS
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores.faiss import DistanceStrategy

from app.config import (
    EMBEDDING_MODEL_PATH,
    FAISS_INDEX_DIR,
    FETCH_K
)
from app.embedding import LocalEmbeddings
LOADER_MAP = {
    ".txt": TextLoader,
    ".md": TextLoader,
    ".pdf": PyPDFLoader,
    ".docx": Docx2txtLoader,
}
INDEX_ROOT = Path(FAISS_INDEX_DIR)

embeddings = LocalEmbeddings(str(EMBEDDING_MODEL_PATH))

splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=80,
    length_function=len,
    separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
)
def ingest(file_path: str, doc_id: str, user_id: str | None) -> int:
    ext = Path(file_path).suffix.lower()
    loader_cls = LOADER_MAP.get(ext)
    if loader_cls is None:
        raise ValueError(f"不支持的文件类型：{ext}")
    kwargs = {"encoding": "utf-8"} if loader_cls is TextLoader else {}
    docs = loader_cls(file_path, **kwargs).load()
    # """入库一个文档：每个文档单独一个索引目录，删除时直接删目录。"""
    # docs = TextLoader(file_path, encoding="utf-8").load()
    chunks = splitter.split_documents(docs)
    for i, c in enumerate(chunks):
        c.metadata.update({"doc_id": doc_id, "chunk_index": i, "user_id": user_id})
    db = FAISS.from_documents(
                        chunks, 
                        embeddings,  
                        distance_strategy=DistanceStrategy.MAX_INNER_PRODUCT, # 使用内积
                        normalize_L2=True # 归一化
                        )
    db.save_local(str(INDEX_ROOT / doc_id))
    return len(chunks)

def delete_doc(doc_id: str) -> None:
    shutil.rmtree(INDEX_ROOT / doc_id, ignore_errors=True)

def load_store():
    """把所有文档的faiss索引合并成一个，没有文档时返回 None。"""
    store = None
    if not INDEX_ROOT.exists():
        return None
    for d in sorted(p for p in INDEX_ROOT.iterdir() if p.is_dir()):
        s = FAISS.load_local(
            str(d), embeddings, allow_dangerous_deserialization=True
        )  # 只加载你自己生成的索引文件，别加载来路不明的
        if store is None:
            store = s
        else:
            store.merge_from(s)
    return store






