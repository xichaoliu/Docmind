import { useAuthStore } from "../store/authStore";
const BASE_URL = import.meta.env.VITE_API_BASE_URL;

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

class ApiError extends Error {
  status: number;
  constructor( status: number, message: string) {
    super(message);
     this.status = status;
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = useAuthStore.getState().token;
  const body = options?.body as unknown as FormData | string;
  const finalHeaders = new Headers({
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    } as HeadersInit);
  if (body instanceof FormData) {
    finalHeaders.delete('Content-Type');
  }
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: finalHeaders,
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, text || response.statusText);
  }
  if (response.status === 401) {
    useAuthStore.getState().logout();
  }

  const json: ApiResponse<T> = await response.json();

  if (json.code !== 200) {
    throw new ApiError(json.code, json.message);   // 业务错误也能被统一捕获
  }


  return  json.data;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
  postForm: <T>(path: string, body?: FormData) => request<T>(path, { method: "POST", body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

export { ApiError };