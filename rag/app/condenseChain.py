from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_ollama import ChatOllama
from app.config import OLLAMA_HOST, OLLAMA_MODEL

# 独立的低温度实例。模型名和 llm 保持一致，Ollama 不需要来回换载模型
condense_llm = ChatOllama(
    base_url=OLLAMA_HOST,
    model=OLLAMA_MODEL,
    temperature=0,
    num_predict=128,        # 改写结果很短，限制长度能明显省时间
)

condense_prompt = ChatPromptTemplate.from_messages([
    ("system", """你是问题改写助手。根据对话历史，把用户的最新问题改写成一个不依赖上下文、可以单独理解的完整问题。
规则：
- 只做改写，绝对不要回答问题
- 把"它""这个""上面提到的"等指代替换成具体对象
- 如果最新问题本身已经完整，原样输出
- 只输出改写后的问题，不要任何解释、前缀或引号"""),
    MessagesPlaceholder("history"),
    ("human", "最新问题：{question}\n改写后的问题："),
])

condense_chain = condense_prompt | condense_llm | StrOutputParser()
# 独立的低温度实例。模型名和 llm 保持一致，Ollama 不需要来回换载模型
