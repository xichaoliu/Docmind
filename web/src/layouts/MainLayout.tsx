import { useState } from "react";
import { Outlet, useNavigate } from 'react-router-dom';
import {  Layout, Button,List, Typography, Modal, Dropdown,Input,Divider,  message } from "antd";
import { PlusOutlined, MenuFoldOutlined, MenuUnfoldOutlined, FileTextOutlined, MoreOutlined, EditOutlined ,DeleteOutlined} from "@ant-design/icons";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchConversations, deleteConversation, updateConversationTitle } from "../api/conversations";

import { useConversationStore } from "../store/chatStore";
import UserProfile from "../components/UserProfile";

const { Sider, Content } = Layout;
const { Text } = Typography;


export default function MainLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [modal, contextHolder] = Modal.useModal();
  const [collapsed, setCollapsed] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingTitle, setEditingTitle] = useState("");

  const activeConversationId = useConversationStore(state => state.activeConversationId)
  const { data: conversations, isLoading } = useQuery({
    queryKey: ["conversations"],
    queryFn: fetchConversations
  });

  const { mutate: rename } = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      updateConversationTitle(id, title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["conversations"] });
      setEditingId(null);
    },
    onError: () => message.error("重命名失败"),
  });
  const { mutate: remove } = useMutation({
    mutationFn: deleteConversation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["conversations"] }),
    onError: () => message.error("删除失败"),
  });

  const handleDelete = (id: string, title: string) => {
    modal.confirm({
      title: "删除对话",
      content: `确定要删除"${title}"吗？此操作不可撤销。`,
      okText: "删除",
      okType: "danger",
      cancelText: "取消",
      onOk: () => remove(id),
    });
  };

  const handleStartNew = () => {
    navigate("/chat");
    useConversationStore.getState().startNewConversation()
  }

  const handleSelect = (id: string) => {
    if (location.pathname !== '/chat') {
      navigate("/chat")         // 已在该页，直接执行
    }
    useConversationStore.getState().setConversationId(id)
  }


  return (
 
    <>
    {contextHolder}
    <Layout style={{ width: "100%", height: "100vh" }}>
      <Sider
        width={260}
        collapsedWidth={0}
        collapsed={collapsed}
        trigger={null}
        style={{
          height: "100vh",           // 关键1：限制侧边栏高度为视口高度
          background: "#fff",
          position: "relative", 
          borderRight: "1px solid #f0f0f0",
          overflow: "hidden"         // 关键4：防止侧边栏自身整体滚动
        }}
      >
        <div style={{ padding: 12, flexShrink: 0 }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            block
            onClick={handleStartNew}
          >
            新建对话
          </Button>
        </div>
        <div style={{padding: '0 12px',  flexShrink: 0 }}>
          <Button icon={<FileTextOutlined />} block onClick={() => navigate("/documents")}>
            文档管理
          </Button>
        </div>
        <Divider style={{ margin: 0, flexShrink: 0 }}/>
          {/* 中间自适应滚动区域 */}
          <div style={{ flex: 1, overflowY: "auto" }}>
       <List 
        loading={isLoading}
        dataSource={conversations ?? []}
        renderItem={(item) => (
          <List.Item 
          onClick={() => handleSelect(item.id)}
          style={{
            padding: "10px 16px",
            cursor: "pointer",
            display: "flex",
            justifyContent: "space-between", 
            flexDirection: "row",
            background: item.id === activeConversationId ? "#f0f5ff" : undefined,
          }}
          >
            <div  style={{
                  display: "flex",
                  alignItems: "center",
                  width: "100%",
                  gap: 8,
                }}>
                  {editingId === item.id ? (
                  <Input
                    size="small"
                    value={editingTitle}
                    autoFocus
                    onChange={(e) => setEditingTitle(e.target.value)}
                    onPressEnter={() => rename({ id: item.id, title: editingTitle })}
                    onBlur={() => rename({ id: item.id, title: editingTitle })}
                    style={{ flex: 1,  minWidth: 0}}
                  />
                ) : (
                  <Text ellipsis  style={{ flex: 1, minWidth: 0 }}>
                    {item.title}
                  </Text>
                )}
                <Dropdown
                  trigger={["click"]}
                  menu={{
                    items: [
                      {
                        key: "rename",
                        label: "重命名",
                        icon: <EditOutlined />,
                        onClick: () => {
                          setEditingId(item.id);
                          setEditingTitle(item.title);
                        },
                      },
                      { type: "divider" },
                      {
                        key: "delete",
                        label: "删除",
                        icon: <DeleteOutlined />,
                        danger: true,
                        onClick: () => handleDelete(item.id, item.title),
                      },
                    ],
                  }}
                >
                  <MoreOutlined
                    onClick={(e:any) => e.stopPropagation()}   // 阻止冒泡，避免触发外层的"选中会话"点击事件
                    style={{ padding: 4, flexShrink: 0 }}
                  />
                </Dropdown>
              </div>
            </List.Item>)}
      />
          </div>
 
      {/* 底部固定区域 */}
    <div style={{ 
      position: "absolute", 
      bottom: 0, 
      left: 0, 
      width: "100%", 
      background: "#fff", // 防止滚动时内容透过来
      zIndex: 10 
    }}>
      <UserProfile />
    </div>
      </Sider>

      <Layout>
        <div
          style={{
            height: 48,
            display: "flex",
            alignItems: "center",
            padding: "0 12px",
            borderBottom: "1px solid #f0f0f0",
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((v) => !v)}
          />
        </div>
        <Content style={{ display: "flex", flexDirection: "column", overflow: "hidden" }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
    </>

  );
}