"""FastAPI HTTP 接口。"""

import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api import chat, document # 把两个路由文件都导进来
logging.basicConfig(level=logging.INFO)


app = FastAPI(title="RAG增强检索接口", version="0.1.0", description="本地 RAG 问答 API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat.router)  
app.include_router(document.router)