// web/src/hooks/parseSSE.test.ts
import { describe, it, expect } from "vitest";
import { parseSSEChunk } from "./parseSSE";

describe("parseSSEChunk", () => {
  it("解析完整的一条消息", () => {
    const { messages, rest } = parseSSEChunk('event: token\ndata: {"text":"你好"}\n\n');
    expect(messages).toEqual([{ event: "token", data: '{"text":"你好"}' }]);
    expect(rest).toBe("");
  });

  it("处理网络分包：半条消息应该被保留到下一次拼接", () => {
    // SSE 基于 TCP 流，
    // 一次 read() 不保证恰好是完整的一条或多条消息，
    // 这个纯函数设计就是为了独立验证这个边界情况
    const { messages, rest } = parseSSEChunk('event: token\ndata: {"text":"前半');
    expect(messages).toEqual([]);
    expect(rest).toBe('event: token\ndata: {"text":"前半');
  });

  it("一次读到多条完整消息，应该全部解析出来", () => {
    const chunk = 'event: token\ndata: {"text":"a"}\n\nevent: token\ndata: {"text":"b"}\n\n';
    const { messages } = parseSSEChunk(chunk);
    expect(messages).toHaveLength(2);
  });
});