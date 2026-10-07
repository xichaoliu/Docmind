"""项目路径与默认资源位置（相对仓库根目录）。"""

from pathlib import Path

import os

OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:latest")
EMBEDDING_MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH", "")
FAISS_INDEX_DIR = os.getenv("FAISS_INDEX_DIR", "")
FETCH_K = os.getenv("FETCH_K", 1000) # 粗召回数量

def get_project_root() -> Path:
    """返回仓库根目录（包含 data、models 的目录）。"""
    return Path(__file__).resolve().parents[1]


PROJECT_ROOT = get_project_root()

# 开发阶段使用, 正式上线时注释掉

# EMBEDDING_MODEL_PATH = (
#     PROJECT_ROOT / "models" / "damo" / "nlp_gte_sentence-embedding_chinese-base"
# )

# FAISS_INDEX_DIR = PROJECT_ROOT / "faiss_index"
