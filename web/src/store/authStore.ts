import { create } from "zustand";
import { persist } from "zustand/middleware";
import { queryClient } from '../queryClient.ts';
import { useConversationStore } from "./chatStore";
const BASE_URL = import.meta.env.VITE_API_BASE_URL;

interface AuthState {
  token: string | null;
  username: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

// 这里先给出接口，默认 token 为 null（视为未登录）。
// 真正接入登录接口后，把 login() 的调用位置换成实际请求返回的 token 即可。

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
        token: null,
        username: null,
        isAuthenticated: false,
        login: async (username: string, password: string) => {
            const res = await fetch(`${BASE_URL}/api/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password }),
            });
            const {code, message:msg, data } = await res.json();   // 对应 Result<T> 包装
            if (code !== 200) {
                // message.error(msg);
                throw new Error(msg);
            }
            set({ token: data.token, username: data.username, isAuthenticated: true });
        },
        register: async (username: string, password: string) => {
            const res = await fetch(`${BASE_URL}/api/auth/register`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password }),
            });
            const {code, message:msg, data } = await res.json();   // 对应 Result<T> 包装
            if (code !== 200) {
               throw new Error(msg);
            }
            set({ token: data.token, username: data.username, isAuthenticated: true });
        },
        logout: () => {
            useConversationStore.getState().startNewConversation();  // 清空聊天状态
            queryClient.clear();
            set({ token: null, username: null, isAuthenticated: false })
        },
    }),
    { name: "docmind-auth" } // localStorage 里的 key
  )
);