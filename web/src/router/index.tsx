import { lazy, Suspense } from "react";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import ProtectedRoute, { GuestOnlyRoute } from "./ProtectedRoute";
import RouteLoading from "./RouteLoading";
import MainLayout from '../layouts/MainLayout'
import NotFoundPage from '../pages/not-found'
import LoginPage from '../pages/login'
 
// 懒加载：路由第一次匹配到时才加载对应代码，减少首屏体积
const ChatPage = lazy(() => import("../pages/chat"));
const DocumentsPage = lazy(() => import("../pages/document"));
 
// 统一包一层 Suspense，避免每个页面都要单独处理加载态
function withSuspense(element: React.ReactNode) {
  return <Suspense fallback={<RouteLoading />}>{element}</Suspense>;
}
 
const router = createBrowserRouter([
  {
    element: <GuestOnlyRoute />, // 已登录用户不能停留在登录页
    children: [{ path: "/login", element: <LoginPage /> }],
  },
  {
    element: <ProtectedRoute />, // 以下路由都需要登录
    children: [
      {
        element: <MainLayout />, // 带侧边栏的主布局，子路由渲染进 <Outlet />
        children: [
          { index: true, element: withSuspense(<ChatPage />) },
          { path: "chat", element: withSuspense(<ChatPage />) },
          { path: "documents", element: withSuspense(<DocumentsPage />) },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> }, // 兜底 404，放在最后
]);

export default function AppRouter() {
  return <RouterProvider router={router} />;
}
