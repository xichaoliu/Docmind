import { useState } from "react";
import { Input, Button, Space } from "antd";
import { SendOutlined, PauseCircleOutlined } from "@ant-design/icons";

const { TextArea } = Input;

interface ChatInputProps {
  children: React.ReactNode;
  disabled?: boolean; // 生成中时为 true，此时展示"停止"而不是"发送"
  onSend: (question: string) => void;
  onStop?: () => void;
}

export default function ChatInput({ disabled, onSend, onStop, children }: ChatInputProps) {
  const [value, setValue] = useState("");

  const handleSend = () => {
    const question = value.trim();
    if (!question) return;
    onSend(question);
    setValue("");
  };

  return (
    <div style={{ padding: 16, borderTop: "1px solid #f0f0f0" }}>
       
        <Space.Compact style={{ width: "100%" }}>
           {children}
          <TextArea
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="向文档提问，Shift + Enter 换行"
            autoSize={{ minRows: 1, maxRows: 5 }}
            disabled={disabled}
          />
          {disabled ? (
            <Button icon={<PauseCircleOutlined />} onClick={onStop} danger>
              停止
            </Button>
          ) : (
            <Button type="primary" icon={<SendOutlined />} onClick={handleSend}>
              发送
            </Button>
          )}
        </Space.Compact>

    </div>
  );
}