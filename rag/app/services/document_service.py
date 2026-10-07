"""入库、加载索引、问答链。。"""
import shutil
from pathlib import Path

from langchain_community.document_loaders import TextLoader, PyPDFLoader, Docx2txtLoader
from langchain_community.vectorstores import FAISS
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_ollama import OllamaLLM
from langchain_ollama import ChatOllama
from langchain_text_splitters import RecursiveCharacterTextSplitter

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
    FAISS.from_documents(chunks, embeddings).save_local(str(INDEX_ROOT / doc_id))
    return len(chunks)

def delete_doc(doc_id: str) -> None:
    shutil.rmtree(INDEX_ROOT / doc_id, ignore_errors=True)

def load_store():
    """把所有文档的索引合并成一个，没有文档时返回 None。"""
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



def get_retriever(k: int = 4, doc_ids: list[str]  | None = None, user_id: str | None = None):
    """每次调用时从当前的 store 生成一个 retriever。"""
    store = load_store()
    if store is None:
        return None
    search_kwargs = {"k": k, "fetch_k": FETCH_K,}
    # 收集所有过滤条件，最后统一组合，避免覆盖
    filters = []
    if doc_ids is not None:                    # ← 区分 None 和 []
        allowed = set(doc_ids)                 # ← set 查找 O(1)，也避免闭包捕获可变对象
        filters.append(lambda meta: meta.get("doc_id") in allowed)
    if user_id is not None:
        filters.append(lambda meta: meta.get("user_id") == user_id)
    if filters:
        search_kwargs["filter"] = lambda meta: all(f(meta) for f in filters)
    return store.as_retriever(search_kwargs=search_kwargs)
