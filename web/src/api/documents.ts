import { apiClient } from "../utils/client";

export interface DocumentItem {
  id: string;
  docId: string;
  fileName: string;
  status: "PROCESSING" | "READY" | "FAILED";
  createdAt: string;
}

export const fetchDocuments = () => apiClient.get<DocumentItem[]>("/api/documents/list");

export const uploadDocument = async (file: File): Promise<void> => {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient.postForm<void>(`/api/documents/upload`, formData);
};

export const deleteDocument = (docId: string) => apiClient.delete<void>(`/api/documents/${docId}`);