import { useRef, useState, useEffect, useCallback } from "react";
import { Empty, message as antdMessage } from "antd";
import MessageBubble from "../../components/MessageBubble";
import ChatInput from "../../components/ChatInput";
import DocumentSelector from "../../components/DocumentSelector";
import { useChatStream } from "../../hooks/useChatStream";
import type { ChatMessage, Citation } from "../../types/chat";
import { useConversationStore } from "../../store/chatStore";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMessages } from "../../api/conversations";

export default function ChatPage() {
    const queryClient = useQueryClient();
  const activeConversationId = useConversationStore((state) => state.activeConversationId);


  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [selectedDocIds, setSelectedDocIds] = useState<string[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  // 当前正在生成的这条 assistant 消息的 id，用于把流式事件定向更新到它身上
  const activeMessageIdRef = useRef<string | null>(null);
  // 记录"由流式过程新建"的会话 id：这种会话的消息已经在本地了，不需要再拉历史
  const createdByStreamRef = useRef<string | null>(null);


  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    console.log('activeConversationId 变成了：', activeConversationId);
 if (activeConversationId && activeConversationId === createdByStreamRef.current) return;
    createdByStreamRef.current = null;
    setMessages([]);
    setSelectedDocIds([]);   // 加这一行
    // 比如可以在这里弹提示、发请求等
  }, [activeConversationId]);

const {data: activeMessages} = useQuery({
    queryKey: ["conversations", activeConversationId, "messages"],
    queryFn: () => fetchMessages(activeConversationId ?? ''),
    enabled: !!activeConversationId && activeConversationId !== createdByStreamRef.current,  // 如果是由流式过程新建的会话，就不再拉历史
    // refetchOnWindowFocus: false,
  })


    useEffect(() => {
    if(activeMessages) {
      setMessages(activeMessages)
    }
    // 比如可以在这里弹提示、发请求等
  }, [activeMessages]);



  const updateActiveMessage = useCallback((updater: (m: ChatMessage) => ChatMessage) => {
    const id = activeMessageIdRef.current;
    if (!id) return;
    setMessages((prev) => prev.map((m) => (m.id === id ? updater(m) : m)));
  }, []);

  const { send, stop, isStreaming } = useChatStream({
    onToken: (text) => {
      updateActiveMessage((m) => ({
        ...m,
        content: m.content + text,
        status: "streaming",
      }));
    },
    onCitations: (citations: Citation[]) => {
      updateActiveMessage((m) => ({ ...m, citations }));
    },
    onDone: () => {
      updateActiveMessage((m) => ({ ...m, status: "done" }));
      activeMessageIdRef.current = null;
    },
    onError: (msg) => {
      updateActiveMessage((m) => ({ ...m, status: "error" }));
      antdMessage.error(msg);
      activeMessageIdRef.current = null;
    },
    onConversationCreated: (conversationId: string) => {
      // 当后端返回 conversation_created 事件时，更新当前会话 ID
      createdByStreamRef.current = conversationId;  // 记录这个会话是由流式过程新建的
      useConversationStore.getState().setConversationId(conversationId);
      queryClient.invalidateQueries({ queryKey: ["conversations"] });
    }
  });



  const handleSend = (question: string) => {
    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: question,
    };
    const assistantMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: "assistant",
      content: "",
      status: "pending", // 还没收到第一个 token 前，展示跳动圆点
    };

    activeMessageIdRef.current = assistantMsg.id;
    setMessages((prev) => [...prev, userMsg, assistantMsg]);
    send({question, conversationId: activeConversationId, docIds: selectedDocIds });
  };

  const handleStop = () => {
    stop();
    updateActiveMessage((m) => ({ ...m, status: "done" }));
    activeMessageIdRef.current = null;
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div ref={scrollRef} style={{ flex: 1, overflowY: "auto", padding: "24px 24px 0" }}>
        {messages.length === 0 ? (
          <Empty description="上传文档后，在这里开始提问" style={{ marginTop: 120 }} />
        ) : (
          messages.map((m) => <MessageBubble key={m.id} message={m} />)
        )}
      </div>
        <div style={{width: "100%"}}>
          <ChatInput disabled={isStreaming} onSend={handleSend} onStop={handleStop} >
           <div style={{width: "200px"}}>
             <DocumentSelector value={selectedDocIds} onChange={setSelectedDocIds} />
           </div>
          </ChatInput>
        </div>
    </div>
  );
}