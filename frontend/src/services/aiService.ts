import { api } from '@/lib/api';
import type { ChatRequest, ChatResponse } from '@/types/ai';

/**
 * Sends a message to the AI assistant through the API gateway.
 */
export async function sendAssistantMessage(request: ChatRequest): Promise<ChatResponse> {
  const response = await api.post<ChatResponse>('/api/v1/ai/chat', request);
  return response.data;
}
