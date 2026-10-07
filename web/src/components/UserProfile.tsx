import { Dropdown, Avatar, Button, Space } from 'antd';
import { UserOutlined, MoreOutlined, LogoutOutlined } from '@ant-design/icons';
import { useAuthStore } from '../store/authStore';

export default function UserProfile(){
    const username = useAuthStore((state) => state.username);  // 从全局状态获取用户名
  // 定义下拉菜单项（仅保留退出登录）

  const items = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ];

  // 处理菜单点击
  const handleMenuClick = ({ key } : any) => {
        console.log("Menu item clicked:", key);
        if (key === 'logout') {
            useAuthStore.getState().logout();
        }
  };

  return (
    <div style={{ padding: '20px 10px', display: 'inline-block' }}>
      {/* 
        Dropdown 组件：
        trigger="click" 点击触发
        placement="topLeft" 菜单向上弹出，对齐左侧
      */}
      <Dropdown
        menu={{ items, onClick: handleMenuClick }}
        trigger={['click']}
        placement="topLeft"
      >
        {/* 用一个自定义样式的 Button 作为触发器和用户信息展示容器 */}
        <Button
          style={{
            height: 'auto',
            padding: '6px',
            borderRadius: '24px', // 胶囊圆角
            // backgroundColor: '#f5f5f7', // 灰色背景
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '230px', // 设定一个固定宽度，可根据容器自适应调整
            boxShadow: 'none',
          }}
        >
          <Space size={10}>
            {/* 头像图标 */}
            <Avatar
              size={24}
              icon={<UserOutlined />}
              style={{ backgroundColor: '#e0e0e0', color: '#888' }}
            />
            {/* 用户名 */}
            <span style={{ color: '#666', fontWeight: 500, fontSize: '14px' }}>
              {username}
            </span>
          </Space>
          
          {/* 更多选项图标 */}
          <MoreOutlined style={{ color: '#999', fontSize: '16px' }} />
        </Button>
      </Dropdown>
    </div>
  );
};
