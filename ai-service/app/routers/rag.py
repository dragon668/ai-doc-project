"""
RAG路由 - SSE流式问答接口
"""
import json
import logging
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.services.rag_service import rag_search_and_answer

logger = logging.getLogger(__name__)
router = APIRouter()


class ChatRequest(BaseModel):
    question: str
    workspace_id: int = 1
    history: List[dict] = []
    doc_ids: Optional[List[int]] = None
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    model: Optional[str] = None


@router.post("/chat")
async def chat(request: ChatRequest):
    """SSE流式RAG问答"""

    async def event_stream():
        try:
            async for chunk in rag_search_and_answer(
                workspace_id=request.workspace_id,
                question=request.question,
                history=request.history,
                doc_ids=request.doc_ids,
                api_key=request.api_key,
                base_url=request.base_url,
                model=request.model
            ):
                yield f"data: {chunk}\n\n"
            yield "data: [DONE]\n\n"
        except Exception as e:
            logger.error(f"Chat error: {e}")
            yield f"data: AI服务异常: {str(e)}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
