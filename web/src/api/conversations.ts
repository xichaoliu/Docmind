import { apiClient } from "../utils/client";

export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Message {
  id: string;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  citations?: Citation[];
  createdAt: string;
}

export interface Citation {
  docId: string;
  chunkIndex: number;
  text: string;
}

export const fetchConversations = () =>
  apiClient.get<Conversation[]>("/api/conversations");

export const fetchMessages = (conversationId: string) =>
  apiClient.get<Message[]>(`/api/conversations/${conversationId}/messages`);

export const updateConversationTitle = (conversationId: string, title: string) =>
  apiClient.patch<void>(`/api/conversations/${conversationId}`, { title });

export const deleteConversation = (conversationId: string) =>
  apiClient.delete<void>(`/api/conversations/${conversationId}`);