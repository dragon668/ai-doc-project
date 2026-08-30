"""
AI文档协作工作台 - Python RAG AI服务
提供文档向量化、RAG检索增强问答功能
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import rag, document

app = FastAPI(title="AI Doc Workspace - RAG Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(rag.router, prefix="/api/rag", tags=["RAG"])
app.include_router(document.router, prefix="/api/document", tags=["Document"])


@app.get("/health")
async def health():
    return {"status": "ok"}
