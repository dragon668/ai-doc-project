"""
RAG服务 - 文档解析、切片、向量化、检索增强问答
"""
import os
import logging
from typing import List, Optional

import numpy as np
from langchain.text_splitter import RecursiveCharacterTextSplitter
from pypdf import PdfReader

from app.services.vector_store import vector_store

logger = logging.getLogger(__name__)

# 配置
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-ada-002")
CHAT_MODEL = os.getenv("CHAT_MODEL", "gpt-3.5-turbo")

# 文本分割器
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
    separators=["\n\n", "\n", "。", "！", "？", ".", " ", ""]
)


def parse_pdf(file_path: str) -> str:
    """解析PDF文件提取文本"""
    reader = PdfReader(file_path)
    text_parts = []
    for page in reader.pages:
        text = page.extract_text()
        if text:
            text_parts.append(text)
    return "\n".join(text_parts)


def split_text(text: str) -> List[str]:
    """将文本分割成块"""
    return text_splitter.split_text(text)


def get_embeddings(texts: List[str], api_key: Optional[str] = None,
                   base_url: Optional[str] = None, model: Optional[str] = None) -> np.ndarray:
    """调用OpenAI API获取文本向量"""
    import httpx

    headers = {
        "Authorization": f"Bearer {api_key or OPENAI_API_KEY}",
        "Content-Type": "application/json"
    }

    # 批量请求，每次最多20条
    all_embeddings = []
    batch_size = 20
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        payload = {
            "model": model or EMBEDDING_MODEL,
            "input": batch
        }

        response = httpx.post(
            f"{(base_url or OPENAI_BASE_URL).rstrip('/')}/embeddings",
            json=payload,
            headers=headers,
            timeout=60.0
        )
        response.raise_for_status()
        data = response.json()

        for item in data["data"]:
            all_embeddings.append(item["embedding"])

    return np.array(all_embeddings, dtype=np.float32)


async def vectorize_document(workspace_id: int, doc_id: int, file_path: str, doc_title: str):
    """解析文档并向量化存储"""
    try:
        # 解析文档
        text = ""
        if file_path.lower().endswith(".pdf"):
            text = parse_pdf(file_path)
        elif file_path.lower().endswith((".md", ".txt")):
            with open(file_path, "r", encoding="utf-8") as f:
                text = f.read()
        else:
            logger.warning(f"Unsupported file type: {file_path}")
            return

        if not text.strip():
            logger.warning(f"Empty document: {file_path}")
            return

        # 文本分割
        chunks = split_text(text)
        if not chunks:
            return

        # 获取向量
        vectors = get_embeddings(chunks)

        # 构建元数据
        metadata = [
            {
                "doc_id": doc_id,
                "doc_title": doc_title,
                "chunk_index": i,
                "content": chunk  # 存储完整chunk内容用于RAG上下文
            }
            for i, chunk in enumerate(chunks)
        ]

        # 存入向量库
        vector_store.add_vectors(workspace_id, vectors, metadata)

        logger.info(f"Vectorized document {doc_id}: {len(chunks)} chunks")

    except Exception as e:
        logger.error(f"Failed to vectorize document {doc_id}: {e}")
        raise


async def rag_search_and_answer(workspace_id: int, question: str,
                                 history: List[dict], doc_ids: Optional[List[int]] = None,
                                 top_k: int = 5, api_key: Optional[str] = None,
                                 base_url: Optional[str] = None, model: Optional[str] = None):
    """RAG检索增强问答 - 生成器，用于SSE流式输出"""
    import httpx

    # 1. 获取问题的向量
    query_vector = get_embeddings([question], api_key, base_url)

    # 2. 在向量库中检索
    search_results = vector_store.search(workspace_id, query_vector, top_k=top_k)

    # 过滤指定文档
    if doc_ids:
        search_results = [(meta, score) for meta, score in search_results
                         if meta.get("doc_id") in doc_ids]

    # 3. 构建上下文
    context_parts = []
    reference_docs = []
    for meta, score in search_results:
        context_parts.append(f"[文档: {meta.get('doc_title', '未知')}] {meta.get('content', '')}")
        if meta.get("doc_id") not in reference_docs:
            reference_docs.append({"doc_id": meta.get("doc_id"), "title": meta.get("doc_title")})

    context = "\n\n".join(context_parts) if context_parts else "未找到相关文档内容"

    # 4. 构建Prompt
    system_prompt = """你是一个专业的文档问答助手。请基于以下检索到的文档内容来回答用户的问题。
如果文档内容中没有相关信息，请如实告知。回答要准确、简洁。

检索到的文档内容：
{context}""".format(context=context)

    messages = [{"role": "system", "content": system_prompt}]

    # 添加历史对话(最近5轮)
    for msg in history[-10:]:
        messages.append({"role": msg["role"], "content": msg["content"]})

    messages.append({"role": "user", "content": question})

    # 5. 调用大模型流式生成
    headers = {
        "Authorization": f"Bearer {OPENAI_API_KEY}",
        "Content-Type": "application/json"
    }

    payload = {
        "model": model or CHAT_MODEL,
        "messages": messages,
        "stream": True,
        "temperature": 0.7,
        "max_tokens": 2000
    }

    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream(
            "POST",
            f"{(base_url or OPENAI_BASE_URL).rstrip('/')}/chat/completions",
            json=payload,
            headers=headers
        ) as response:
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:]
                    if data == "[DONE]":
                        yield "[DONE]"
                        return
                    try:
                        import json
                        chunk = json.loads(data)
                        delta = chunk.get("choices", [{}])[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except Exception:
                        continue
