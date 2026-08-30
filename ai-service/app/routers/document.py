"""
文档处理路由 - 文档解析向量化接口(供Java后端调用)
"""
import os
import logging
import tempfile
from typing import List, Optional

from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel

from app.services.rag_service import vectorize_document

logger = logging.getLogger(__name__)
router = APIRouter()


class VectorizeRequest(BaseModel):
    workspace_id: int
    doc_id: int
    doc_title: str
    doc_type: str
    minio_url: str  # MinIO预签名下载URL


@router.post("/vectorize")
async def vectorize(request: VectorizeRequest):
    """接收Java后端的向量化请求"""
    import httpx

    try:
        # 从MinIO下载文件
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.get(request.minio_url)
            response.raise_for_status()

        # 保存到临时文件
        suffix = f".{request.doc_type}"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(response.content)
            tmp_path = tmp.name

        # 解析并向量化
        await vectorize_document(
            workspace_id=request.workspace_id,
            doc_id=request.doc_id,
            file_path=tmp_path,
            doc_title=request.doc_title
        )

        # 清理临时文件
        os.unlink(tmp_path)

        return {"status": "ok", "doc_id": request.doc_id}

    except Exception as e:
        logger.error(f"Vectorize failed: doc_id={request.doc_id}", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/vectorize/upload")
async def vectorize_upload(
    workspace_id: int = Form(...),
    doc_id: int = Form(...),
    doc_title: str = Form(...),
    file: UploadFile = File(...)
):
    """直接上传文件并向量化"""
    try:
        suffix = os.path.splitext(file.filename)[1] if file.filename else ".pdf"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        await vectorize_document(
            workspace_id=workspace_id,
            doc_id=doc_id,
            file_path=tmp_path,
            doc_title=doc_title
        )

        os.unlink(tmp_path)
        return {"status": "ok", "doc_id": doc_id}

    except Exception as e:
        logger.error(f"Vectorize upload failed: doc_id={doc_id}", e)
        raise HTTPException(status_code=500, detail=str(e))
