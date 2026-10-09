

from langchain_core.messages import HumanMessage, AIMessage

from app.chains import qa_chain, format_docs
from app.condenseChain import condense_chain
from app.services.retriever_service import get_retriever

import logging
logger = logging.getLogger(__name__)

CONDENSE_MAX_MESSAGES = 6      # 只看最近 3 轮
CONDENSE_MAX_CHARS = 300       # 每条历史截断，助手的长回答会拖慢改写

def condense_question(question: str, history) -> str:
    if not history:
        return question

    recent = [
        type(m)(role=m.role, content=m.content[:CONDENSE_MAX_CHARS])
        for m in history[-CONDENSE_MAX_MESSAGES:]
    ]
    try:
        rewritten = condense_chain.invoke({
            "question": question,
            "history": to_lc_messages(recent),
        }).strip().strip('"“”')
    except Exception as e:
        logger.warning("问题改写失败，使用原问题：%s", e)
        return question

    # 小模型可能直接开始回答，或输出空内容，都退回原问题
    if not rewritten or len(rewritten) > max(200, len(question) * 5):
        return question
    return rewritten
def to_lc_messages(history):
    return [
        HumanMessage(content=m.content) if m.role == "user" else AIMessage(content=m.content)
        for m in history
    ]

"""检索 + 流式生成,是一个生成器,逐块产出 (事件类型, 数据)。"""
def answer_stream(question: str, history=None, doc_ids=None, user_id=None):
    history = history or []
    retriever = get_retriever(doc_ids=doc_ids, user_id=user_id)

    if retriever is None:
        # 还没有任何文档入库
        yield ("error", {"message": "知识库为空,请先上传文档"})
        return
    # 改写问题，适配检索(查询改写)
    search_query = condense_question(question, history)
    logger.info("文档ID: %s", doc_ids)
    logger.info("检索问题：%s -> %s", question, search_query)
    docs = retriever.invoke(search_query)   # 检索,一次性完成

    if not docs:
        # 检索不到相关内容,直接返回
        yield ("token", {"text": "没有检索到相关内容，请换一种问法。"})
        yield ("done", {})
        return
    else:
        context = format_docs(docs)

    try:
        for token in qa_chain.stream({
            "context": context, 
            "question": question, 
            "history": to_lc_messages(history)}):
            yield ("token", {"text": token})

        yield ("citations", [
            {
                "docId": d.metadata.get("doc_id"),
                "userId": d.metadata.get("user_id"),
                "chunkIndex": d.metadata.get("chunk_index"),
                "text": d.page_content,
            }
            for d in docs
        ])
        yield ("done", {})
    except Exception as e:
        yield ("error", {"message": str(e)})