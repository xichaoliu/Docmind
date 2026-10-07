"""LLM、Prompt 与问答链的定义。"""

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_ollama import ChatOllama
from app.config import OLLAMA_HOST, OLLAMA_MODEL

llm = ChatOllama(
    base_url=OLLAMA_HOST,
    model=OLLAMA_MODEL,
    temperature=0.3,
    num_predict=2048,
)

# prompt = ChatPromptTemplate.from_template("""
# 请严格根据以下【资料】回答用户的问题。
# - 只使用【资料】中的信息，不要添加资料之外的内容。
# - 引用资料时在句末标注编号，如 [1]、[2]。
# - 如果资料中没有答案，请直接说“资料未提供相关信息”。
# - 历史对话只用来理解指代（如"它""上面那个"），事实必须来自【资料】。

# 【资料】
# {context}
# 【用户问题】
# {question}

# 【你的回答】
# """)

prompt = ChatPromptTemplate.from_messages([
    ("system", """请严格根据以下【资料】回答用户的问题。
- 只使用【资料】中的信息，不要添加资料之外的内容。
- 引用资料时在句末标注编号，如 [1]、[2]。
- 如果资料中没有答案，请直接说"资料未提供相关信息"。
- 如果【资料】为空，只回答"资料未提供相关信息"，不要在句末标注编号。
- 历史对话只用来理解指代（如"它""上面那个"），事实必须来自【资料】。

【资料】
{context}"""),
    MessagesPlaceholder("history"),
    ("human", "{question}"),
])

qa_chain =  prompt | llm | StrOutputParser()

def format_docs(docs) -> str:
    # 加上编号，方便模型在回答里写 [1]、[2]，和前端的引用卡片对应
    return "\n\n".join(f"[{i + 1}] {d.page_content}" for i, d in enumerate(docs))