import { useState } from "react";
import { Button, Card, Form, Input, Tabs, message } from "antd";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";

export default function LoginPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { username: string; password: string }, isRegister: boolean) => {
    setLoading(true);
    try {
      isRegister ? await useAuthStore.getState().register(values.username, values.password) : await useAuthStore.getState().login(values.username, values.password);
      navigate("/chat");
    } catch (e) {
      message.error(e instanceof Error ? e.message : "操作失败");
    } finally {
      setLoading(false);
    }
  };

  const renderForm = (isRegister: boolean) => (
    <Form layout="vertical" onFinish={(v) => onFinish(v, isRegister)}>
      <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
        <Input autoComplete="username" />
      </Form.Item>
      <Form.Item
        name="password"
        label="密码"
        rules={[{ required: true }, ...(isRegister ? [{ min: 8, message: "至少 8 位" }] : [])]}
      >
        <Input.Password autoComplete={isRegister ? "new-password" : "current-password"} />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>
        {isRegister ? "注册并登录" : "登录"}
      </Button>
    </Form>
  );

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
      <Card style={{ width: 380 }}>
        <Tabs
          items={[
            { key: "login", label: "登录", children: renderForm(false) },
            { key: "register", label: "注册", children: renderForm(true) },
          ]}
        />
      </Card>
    </div>
  );
}
