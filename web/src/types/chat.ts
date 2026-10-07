export interface Citation {
    docId: string;
    chunkIndex: number;
    text: string;
  }
  
  export interface ChatMessage {
    id: string;
    role: "user" | "assistant";
    content: string;
    citations?: Citation[];
    status?: "pending" | "streaming" | "done" | "error";
  }
  
  export interface Conversation {
    id: string;
    title: string;
    updatedAt: string;
  }