import { Avatar, Collapse, Typography } from "antd";
import { UserOutlined, RobotOutlined } from "@ant-design/icons";
import type { ChatMessage } from "../types/chat";

const { Text, Paragraph } = Typography;

interface MessageBubbleProps {
  message: ChatMessage;
}

export default function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === "user";

  return (
    <div
      style={{
        display: "flex",
        gap: 12,
        flexDirection: isUser ? "row-reverse" : "row",
        marginBottom: 20,
      }}
    >
      <Avatar
        icon={isUser ? <UserOutlined /> : <RobotOutlined />}
        style={{ background: isUser ? "#1677ff" : "#722ed1", flexShrink: 0 }}
      />

      <div style={{ maxWidth: "72%" }}>
        <div
          style={{
            background: isUser ? "#1677ff" : "#f5f5f5",
            color: isUser ? "#fff" : "rgba(0,0,0,0.88)",
            borderRadius: 8,
            padding: "10px 14px",
          }}
        >
          <Paragraph
            style={{ margin: 0, color: "inherit", whiteSpace: "pre-wrap" }}
          >
            {message.content}
            {message.status === "streaming" && <BlinkCursor />}
          </Paragraph>
        </div>

        {message.status === "pending" && <TypingDots />}

        {message.citations && message.citations.length > 0 && (
          <Collapse
            ghost
            size="small"
            style={{ marginTop: 6 }}
            items={[
              {
                key: "citations",
                label: (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    引用来源（{message.citations.length}）
                  </Text>
                ),
                children: message.citations.map((c, i) => (
                  <div key={i} style={{ marginBottom: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      [{i + 1}] {c.docId} · 片段 {c.chunkIndex}
                    </Text>
                    <div style={{ fontSize: 13, color: "rgba(0,0,0,0.65)" }}>
                      {c.text}
                    </div>
                  </div>
                )),
              },
            ]}
          />
        )}
      </div>
    </div>
  );
}

function BlinkCursor() {
  return (
    <span
      style={{
        display: "inline-block",
        width: 2,
        height: 14,
        marginLeft: 2,
        background: "currentColor",
        animation: "blink 1s steps(2) infinite",
        verticalAlign: "middle",
      }}
    />
  );
}

function TypingDots() {
  return (
    <div style={{ display: "flex", gap: 4, padding: "8px 0" }} role="status" aria-label="正在生成">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          style={{
            width: 6,
            height: 6,
            borderRadius: "50%",
            background: "#bfbfbf",
            animation: "bounce 1.2s infinite",
            animationDelay: `${i * 150}ms`,
          }}
        />
      ))}
    </div>
  );
}