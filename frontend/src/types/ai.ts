/**
 * Request and response contracts for the AI assistant chat endpoint.
 * Mirrors ChatRequest.java and ChatResponse.java.
 */
export interface ChatRequest {
  message: string;
  conversationId?: string | null;
}

export interface ChatResponse {
  reply: string | null;
  conversationId: string | null;
}
