from typing import List

from langchain_core.embeddings import Embeddings
from sentence_transformers import SentenceTransformer


class LocalEmbeddings(Embeddings):
    """基于本地 SentenceTransformer 的 LangChain Embeddings 实现。"""

    def __init__(self, model_path: str):
        self.model = SentenceTransformer(model_path)

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        embeddings = self.model.encode(texts, normalize_embeddings=True)
        return embeddings.tolist()

    def embed_query(self, text: str) -> List[float]:
        return self.embed_documents([text])[0]
