# rag/tests/test_chat_service.py
# 测试改写功能
import pytest
from unittest.mock import patch, MagicMock
from app.services.chat_service import answer_stream
from app.services.chat_service import condense_question

class FakeMessage:
    def __init__(self, role, content):
        self.role = role
        self.content = content

def test_condense_question_no_history_returns_original():
    """没有历史时不应该调用改写模型，直接返回原问题"""
    result = condense_question("DocMind 用什么向量数据库？", [])
    assert result == "DocMind 用什么向量数据库？"

@patch("app.services.chat_service.condense_chain")
def test_condense_question_topic_switch_should_not_carry_old_topic(mock_chain):
    """换话题时，改写结果不应该还带着旧话题的关键词"""
    mock_chain.invoke.return_value = "Ollama 是什么？"  # 模拟模型正确识别换话题
    history = [
        FakeMessage("user", "DocMind 用什么向量数据库？"),
        FakeMessage("assistant", "DocMind 使用 FAISS。"),
    ]
    result = condense_question("换个话题，Ollama 是什么？", history)
    print("改写结果:", result)
    assert "FAISS" not in result
    assert "Ollama" in result

@patch("app.services.chat_service.condense_chain")
def test_condense_question_fallback_on_model_error(mock_chain):
    """改写模型调用失败时，应该降级返回原问题，不能让整个请求崩溃"""
    mock_chain.invoke.side_effect = Exception("模型超时")
    result = condense_question("它是什么？", [FakeMessage("user", "问过的问题")])
    assert result == "它是什么？"

@patch("app.services.chat_service.get_retriever")
def test_empty_knowledge_base_returns_error_event(mock_get_retriever):
    """知识库为空时，应该直接发 error 事件，不应该尝试调用大模型"""
    mock_get_retriever.return_value = None
    events = list(answer_stream("任意问题"))
    assert events[0][0] == "error"
    assert "知识库为空" in events[0][1]["message"]