import { useCallback, useRef, useState } from "react";
import { parseSSEChunk } from "./parseSSE";
import type { Citation } from "../types/chat";
import { useAuthStore } from "../store/authStore";

const CHAT_STREAM_URL = import.meta.env.VITE_API_BASE_URL;

interface UseChatStreamCallbacks {
  onToken: (text: string) => void;
  onCitations: (citations: Citation[]) => void;
  onDone: () => void;
  onError: (message: string) => void;
  onConversationCreated: (conversationId: string) => void;  // 新增一个回调，通知外层更新当前会话 ID
}

export function useChatStream({ onToken, onCitations, onDone, onError, onConversationCreated }: UseChatStreamCallbacks) {
  const [isStreaming, setIsStreaming] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  const send = useCallback(
    async (data: { question: string ; conversationId: string | null, docIds?: string[]}) => {
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setIsStreaming(true);

      try {
        const response = await fetch(`${CHAT_STREAM_URL}/api/chat`, {
          method: "POST",
          headers: { 
            "Content-Type": "application/json",
            "Authorization": `Bearer ${useAuthStore.getState().token}`,
           },
          body: JSON.stringify(data),
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          onError(`请求失败：${response.status}`);
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const { messages, rest } = parseSSEChunk(buffer);
          buffer = rest;

          for (const msg of messages) {
            handleMessage(msg.event, msg.data);
          }
        }
      } catch (err) {
        // AbortError 是用户主动点击"停止"触发的，不算错误，不用提示
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        onError(err instanceof Error ? err.message : "网络请求出错");
      } finally {
        setIsStreaming(false);
        abortControllerRef.current = null;
      }

      function handleMessage(event: string, data: string) {
        try {
          const parsed = JSON.parse(data);
          switch (event) {
            case "token":
              onToken(parsed.text);
              break;
            case "citations":
              onCitations(parsed as Citation[]);
              break;
            case "done":
              onDone();
              break;
            case "error":
              onError(parsed.message ?? "生成过程中出错");
              break;
            case "conversation_created":
              const { conversationId } = JSON.parse(data);
              onConversationCreated(conversationId);  // 新增一个回调，通知外层更新当前会话 ID
            break;
            default:
              // 未知事件类型，忽略即可，避免后端加了新事件时前端直接报错
              break;
          }
        } catch {
          onError("响应解析失败");
        }
      }
    },
    [onToken, onCitations, onDone, onError]
  );

  const stop = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  return { send, stop, isStreaming };
}