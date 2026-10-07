import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

/**
 * 受保护路由：未登录时重定向到 /login，
 * 并把当前路径存进 state.from，登录成功后可以跳回来。
 *
 * 用法（见 routes.tsx）：
 * <Route element={<ProtectedRoute />}>
 *   <Route path="/chat" element={<ChatPage />} />
 * </Route>
 */
export default function ProtectedRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}

/**
 * 反向守卫：已登录时访问 /login，直接跳回首页，
 * 避免登录用户还能看到登录表单。
 */
export function GuestOnlyRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}