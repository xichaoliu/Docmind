
import numpy as np
import re, jieba
from langchain_community.retrievers import BM25Retriever
from langchain_classic.retrievers import EnsembleRetriever, ContextualCompressionRetriever
from langchain_classic.retrievers.document_compressors import CrossEncoderReranker
from langchain_community.cross_encoders import HuggingFaceCrossEncoder
from langchain_core.retrievers import BaseRetriever
from langchain_core.documents import Document
from pydantic import Field
from app.services.document_service import load_store
import logging
logger = logging.getLogger(__name__)
from app.config import (
    FETCH_K,
    HYBRID_ENABLED,
    RERANK_ENABLED,
    RERANKER_MODEL
)



class EmptyRetriever(BaseRetriever):
    """一个永远返回空结果的检索器，用于语料为空时兜底"""
    k: int = Field(default=10)

    def _get_relevant_documents(self, query: str, **kwargs) -> list[Document]:
        return []
# 领域专名先加进词典，避免被切碎
for w in ["RAG", "BM25", "条款", "条件", "软件"]:
    jieba.add_word(w)
# 分词器
STOPWORDS = {"的", "了", "是", "在", "和", "与", "或", "有", "被", "把"}

def jieba_tokenize(text: str) -> list[str]:
    text = re.sub(r"[^\u4e00-\u9fa5a-zA-Z0-9\s]", " ", text)
    tokens = jieba.lcut(text.lower())
    return [t for t in tokens if t.strip() and t not in STOPWORDS]

def get_retriever(query: str | None = None, k: int = 50, doc_ids: list[str]  | None = None, user_id: str | None = None):
    """每次调用时从当前的 store 生成一个 retriever。"""
    store = load_store()
    if store is None:
        return None

    retriever = None
    # 向量检索
    vectorRetriever = vector_retriever(k=k, store=store, doc_ids=doc_ids, user_id=user_id)
    retriever = vectorRetriever
    if HYBRID_ENABLED:
        logger.info("开启混合检索")
        # BM25 检索
        bm25Retriever = bm25_retriever(k=k, store=store, doc_ids=doc_ids, user_id=user_id)
        # 混合检索器（RRF 融合）
        retriever = EnsembleRetriever(
        retrievers=[vectorRetriever, bm25Retriever],
        weights=[0.5, 0.5]
        )
    if RERANK_ENABLED:
        logger.info("开启重排序")
        # ========== 6. 重排序 ==========
        cross_encoder = HuggingFaceCrossEncoder(
            model_name=str(RERANKER_MODEL),
            model_kwargs={"device": "cpu"},
        )
        reranker = CrossEncoderReranker(model=cross_encoder, top_n=5)

        # ========== 7. 完整管道 ==========
        retriever = ContextualCompressionRetriever(
            base_compressor=reranker,
            base_retriever=retriever,
        )
   
    return retriever


def bm25_retriever(k: int = 10, store=None, doc_ids: list[str]  | None = None, user_id: str | None = None):
    all_docs = []
    # 遍历 index_to_docstore_id 映射，根据 docstore_id 查找文档
    for doc_id in store.index_to_docstore_id.values():
        doc = store.docstore.search(doc_id)
        if doc and doc.metadata.get("doc_id") in doc_ids and doc.metadata.get("user_id") == user_id:
            all_docs.append(doc)

    print(f"bm25检索：成功从 FAISS 索引中提取了 {len(all_docs)} 个文档。")
    # 过滤掉 page_content 为空的文档
    all_docs = [d for d in all_docs if d.page_content and d.page_content.strip()]

    # ★ 关键：判断"有效 token"而不是"文档数"
    total_tokens = sum(len(jieba_tokenize(d.page_content)) for d in all_docs)
    if not all_docs or total_tokens == 0:
        print(f"[bm25] 无有效语料，返回空检索器 (docs={len(all_docs)}, tokens={total_tokens})")
        return EmptyRetriever(k=k)

    return BM25Retriever.from_documents(
        all_docs, 
        preprocess_func=jieba_tokenize, 
        bm25_params={"k1": 1.5, "b": 0.75},
        k=k
    )

def vector_retriever(k: int = 4, store=None, doc_ids: list[str]  | None = None, user_id: str | None = None):
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

