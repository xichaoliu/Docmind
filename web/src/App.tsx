import AppRouter from './router'

import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";

function App() {

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{ token: { colorPrimary: "#1677ff", borderRadius: 6 } }}
    >
      <AppRouter />
    </ConfigProvider>
  );
}

export default App
