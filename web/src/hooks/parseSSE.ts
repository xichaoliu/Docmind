/**
 * 把 SSE 原始文本切分成一条条 { event, data } 消息。
 * SSE 协议：每条消息以空行（\n\n）结束，内部可能有 event: 和 data: 两行。
 *
 * 这是纯函数，方便单独测试，不依赖 fetch 或 DOM。
 */
export interface SSEMessage {
    event: string;
    data: string;
  }
  
  export function parseSSEChunk(buffer: string): { messages: SSEMessage[]; rest: string } {
    const parts = buffer.split("\n\n");
    // 最后一段可能是不完整的消息（网络分包导致），留到下次和新数据拼接
    const rest = parts.pop() ?? "";
  
    const messages: SSEMessage[] = [];
    for (const part of parts) {
      if (!part.trim()) continue;
  
      let event = "message"; // SSE 默认事件名
      let data = "";
  
      for (const line of part.split("\n")) {
        if (line.startsWith("event:")) {
          event = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          // data 可能有多行，按协议应拼接；这里简单处理为覆盖（每条消息只有一行 data 时够用）
          data += (data ? "\n" : "") + line.slice(5).trim();
        }
      }
  
      if (data) messages.push({ event, data });
    }
  
    return { messages, rest };
  }