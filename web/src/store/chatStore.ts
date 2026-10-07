// stores/conversationStore.ts
import { create } from "zustand";

interface ConversationState {
  activeConversationId: string | null;

  selectConversation: (id: string) => void;
  startNewConversation: () => void;
  setConversationId: (id: string) => void;  // SSE 首次返回新ID时调用
}

export const useConversationStore = create<ConversationState>((set) => ({
  activeConversationId: null,

  selectConversation: (id) => set({ activeConversationId: id }),

  startNewConversation: () => set({ activeConversationId: null }),

  setConversationId: (id) => set({ activeConversationId: id }),
}));