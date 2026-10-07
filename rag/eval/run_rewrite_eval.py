"""对比「查询改写」与「直接检索」的命中率。

用法：
    cd rag
    python -m eval.run_rewrite_eval                    # 用默认语料
    python -m eval.run_rewrite_eval --doc /path/to.md  # 指定语料
    python -m eval.run_rewrite_eval --k 4 --fetch-k 1000

设计要点：
  - 索引全部在内存里构建，**不落盘**，不会污染 rag/faiss_index/
  - 改写走 app.services.chat_service.condense_question，和线上完全一致
  - 命中判断：top-k 中存在一个 chunk 同时包含该条 gold 里的全部关键词

前提：Ollama 在跑（改写要调模型）。直接检索那一组不需要模型。
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

logging.disable(logging.CRITICAL)

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_community.document_loaders import TextLoader  # noqa: E402
from langchain_community.vectorstores import FAISS  # noqa: E402

from app.services.chat_service import condense_question  # noqa: E402
from app.services.document_service import embeddings, splitter  # noqa: E402

DEFAULT_DOC = Path("/Users/xichaoliu/Desktop/测试文档/apple软件许可协议 .md")
DATASET = Path(__file__).parent / "rewrite-eval.jsonl"


@dataclass
class Turn:
    """condense_question 需要 role / content 两个属性。"""
    role: str
    content: str


def load_dataset() -> list[dict]:
    return [json.loads(l) for l in DATASET.read_text(encoding="utf-8").splitlines() if l.strip()]


def build_store(doc_path: Path) -> FAISS:
    """在内存里建索引，不落盘。"""
    docs = TextLoader(str(doc_path), encoding="utf-8").load()
    chunks = splitter.split_documents(docs)
    for i, c in enumerate(chunks):
        c.metadata.update({"doc_id": "eval-doc", "chunk_index": i})
    return FAISS.from_documents(chunks, embeddings)


def is_hit(hits, gold: list[str]) -> bool:
    return any(all(g in h.page_content for g in gold) for h in hits)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--doc", type=Path, default=DEFAULT_DOC)
    ap.add_argument("--k", type=int, default=4, help="送进 prompt 的条数")
    ap.add_argument("--fetch-k", type=int, default=1000, help="粗召回条数，和线上一致")
    args = ap.parse_args()

    if not args.doc.exists():
        sys.exit(f"语料不存在: {args.doc}")

    print(f"语料: {args.doc.name}")
    store = build_store(args.doc)
    print(f"切分: {store.index.ntotal} 个 chunk    k={args.k}  fetch_k={args.fetch_k}\n")

    items = load_dataset()
    stats = defaultdict(lambda: {"raw": 0, "rw": 0, "n": 0})
    changed, helped, hurt = [], [], []

    for it in items:
        # history：用前几轮的用户问题（没有助手回复，是可复现的近似）
        history = [Turn(role="user", content=t) for t in it.get("turns", [])]
        q = it["test_question"]

        raw_hits = store.similarity_search(q, k=args.k, fetch_k=args.fetch_k)
        raw_ok = is_hit(raw_hits, it["gold"])

        rewritten = condense_question(q, history) if history else q
        if rewritten != q:
            rw_hits = store.similarity_search(rewritten, k=args.k, fetch_k=args.fetch_k)
            changed.append((it["id"], q, rewritten))
        else:
            rw_hits = raw_hits
        rw_ok = is_hit(rw_hits, it["gold"])

        t = it["type"]
        stats[t]["n"] += 1
        stats[t]["raw"] += raw_ok
        stats[t]["rw"] += rw_ok
        stats["ALL"]["n"] += 1
        stats["ALL"]["raw"] += raw_ok
        stats["ALL"]["rw"] += rw_ok

        if rw_ok and not raw_ok:
            helped.append(it["id"])
        elif raw_ok and not rw_ok:
            hurt.append((it["id"], q, rewritten))

    # ---------------- 结果 ----------------
    print("=" * 66)
    print(f"{'类型':<14}{'条数':>6}{'直接检索':>12}{'查询改写':>12}{'变化':>10}")
    print("-" * 66)
    labels = {"pronoun": "指代型", "ellipsis": "省略型",
              "continuation": "话题延续", "control": "对照组", "ALL": "合计"}
    for t in ["pronoun", "ellipsis", "continuation", "control", "ALL"]:
        s = stats[t]
        if not s["n"]:
            continue
        r1, r2 = s["raw"] / s["n"], s["rw"] / s["n"]
        print(f"{labels[t]:<14}{s['n']:>6}{r1:>11.0%}{r2:>12.0%}{(r2 - r1):>+10.0%}")
    print("=" * 66)

    print(f"\n改写实际改变了查询的: {len(changed)}/{len(items)} 条")
    if len(changed) < len(items):
        print(f"  （其余 {len(items) - len(changed)} 条改写结果和原问题相同，两组必然同分）")

    print(f"\n改写后新命中（改写赢）: {len(helped)} 条  {helped}")
    print(f"改写后反而漏掉（改写输）: {len(hurt)} 条")
    for i, q, rw in hurt:
        print(f"  id={i}  原问: {q}\n         改写: {rw}")

    print("\n--- 改写的实际效果抽样 ---")
    for i, q, rw in changed[:8]:
        print(f"  id={i:2}  {q}  →  {rw}")


if __name__ == "__main__":
    main()
