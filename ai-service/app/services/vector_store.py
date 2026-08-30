"""
向量存储服务 - 管理FAISS向量库
"""
import os
import json
import logging
from typing import List, Optional, Tuple
from pathlib import Path

import faiss
import numpy as np

logger = logging.getLogger(__name__)

# 向量维度 (OpenAI text-embedding-ada-002 = 1536)
EMBEDDING_DIM = 1536
VECTOR_DIR = Path("./vector_store")
VECTOR_DIR.mkdir(exist_ok=True)


class VectorStore:
    """基于FAISS的向量存储，每个workspace一个独立的索引"""

    def __init__(self):
        self.indexes: dict[int, faiss.IndexFlatIP] = {}

    def _get_index_path(self, workspace_id: int) -> Path:
        return VECTOR_DIR / f"workspace_{workspace_id}.index"

    def _get_meta_path(self, workspace_id: int) -> Path:
        return VECTOR_DIR / f"workspace_{workspace_id}_meta.json"

    def load_index(self, workspace_id: int) -> faiss.IndexFlatIP:
        """加载或创建workspace的向量索引"""
        if workspace_id in self.indexes:
            return self.indexes[workspace_id]

        index_path = self._get_index_path(workspace_id)
        if index_path.exists():
            index = faiss.read_index(str(index_path))
            self.indexes[workspace_id] = index
            logger.info(f"Loaded vector index for workspace {workspace_id}, ntotal={index.ntotal}")
        else:
            index = faiss.IndexFlatIP(EMBEDDING_DIM)
            self.indexes[workspace_id] = index
            logger.info(f"Created new vector index for workspace {workspace_id}")

        return index

    def save_index(self, workspace_id: int):
        """持久化向量索引到磁盘"""
        if workspace_id in self.indexes:
            index_path = self._get_index_path(workspace_id)
            faiss.write_index(self.indexes[workspace_id], str(index_path))
            logger.info(f"Saved vector index for workspace {workspace_id}")

    def add_vectors(self, workspace_id: int, vectors: np.ndarray, metadata: List[dict]):
        """添加向量到索引"""
        index = self.load_index(workspace_id)

        # 归一化向量用于余弦相似度
        faiss.normalize_L2(vectors)
        index.add(vectors)

        # 保存元数据
        meta_path = self._get_meta_path(workspace_id)
        existing_meta = []
        if meta_path.exists():
            with open(meta_path, "r", encoding="utf-8") as f:
                existing_meta = json.load(f)
        existing_meta.extend(metadata)
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(existing_meta, f, ensure_ascii=False)

        self.save_index(workspace_id)
        logger.info(f"Added {len(vectors)} vectors to workspace {workspace_id}")

    def search(self, workspace_id: int, query_vector: np.ndarray, top_k: int = 5) -> List[Tuple[dict, float]]:
        """检索相似向量"""
        index = self.load_index(workspace_id)
        if index.ntotal == 0:
            return []

        faiss.normalize_L2(query_vector)
        scores, indices = index.search(query_vector, min(top_k, index.ntotal))

        # 加载元数据
        meta_path = self._get_meta_path(workspace_id)
        if not meta_path.exists():
            return []

        with open(meta_path, "r", encoding="utf-8") as f:
            all_meta = json.load(f)

        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < len(all_meta) and idx >= 0:
                results.append((all_meta[idx], float(score)))

        return results

    def delete_workspace_index(self, workspace_id: int):
        """删除workspace的向量索引"""
        if workspace_id in self.indexes:
            del self.indexes[workspace_id]
        index_path = self._get_index_path(workspace_id)
        meta_path = self._get_meta_path(workspace_id)
        if index_path.exists():
            index_path.unlink()
        if meta_path.exists():
            meta_path.unlink()


# 全局单例
vector_store = VectorStore()
