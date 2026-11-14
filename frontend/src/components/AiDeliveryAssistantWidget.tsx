'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Bot, MessageCircle, RotateCcw, Send, X } from 'lucide-react';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import { sendAssistantMessage } from '@/services/aiService';

type MessageRole = 'user' | 'assistant';

interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  isError?: boolean;
}

const MAX_MESSAGE_LENGTH = 2000;
const INITIAL_MESSAGE: ChatMessage = {
  id: 'assistant-welcome',
  role: 'assistant',
  content:
    'Hi! I can help you track a parcel or estimate delivery rates. What would you like to know?',
};

const SUGGESTED_QUESTIONS = [
  'Where is my parcel?',
  'How much does shipping cost?',
];

function createMessageId() {
  return `assistant-message-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function getErrorMessage(error: unknown) {
  if (
    error &&
    typeof error === 'object' &&
    'response' in error &&
    error.response &&
    typeof error.response === 'object' &&
    'data' in error.response &&
    error.response.data &&
    typeof error.response.data === 'object' &&
    'detail' in error.response.data &&
    typeof error.response.data.detail === 'string'
  ) {
    return error.response.data.detail;
  }

  return 'I could not reach the delivery assistant right now. Please try again.';
}

export default function AiDeliveryAssistantWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([INITIAL_MESSAGE]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [failedMessage, setFailedMessage] = useState<string | null>(null);
  const endOfMessagesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endOfMessagesRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isOpen]);

  const sendMessage = useCallback(
    async (rawMessage: string, addUserMessage = true) => {
      const message = rawMessage.trim();
      if (!message || loading) {
        return;
      }

      if (message.length > MAX_MESSAGE_LENGTH) {
        setMessages((current) => [
          ...current.filter((item) => !item.isError),
          {
            id: createMessageId(),
            role: 'assistant',
            content: `Please keep your message under ${MAX_MESSAGE_LENGTH} characters.`,
            isError: true,
          },
        ]);
        setFailedMessage(null);
        return;
      }

      setMessages((current) => {
        const withoutErrors = current.filter((item) => !item.isError);
        return addUserMessage
          ? [
              ...withoutErrors,
              { id: createMessageId(), role: 'user', content: message },
            ]
          : withoutErrors;
      });
      setInput('');
      setFailedMessage(null);
      setLoading(true);

      try {
        const response = await sendAssistantMessage({
          message,
          conversationId,
        });

        setConversationId(response.conversationId || conversationId);
        setMessages((current) => [
          ...current.filter((item) => !item.isError),
          {
            id: createMessageId(),
            role: 'assistant',
            content:
              response.reply?.trim() ||
              'I received your request, but I do not have a response yet.',
          },
        ]);
      } catch (error: unknown) {
        setFailedMessage(message);
        setMessages((current) => [
          ...current.filter((item) => !item.isError),
          {
            id: createMessageId(),
            role: 'assistant',
            content: getErrorMessage(error),
            isError: true,
          },
        ]);
      } finally {
        setLoading(false);
      }
    },
    [conversationId, loading]
  );

  const handleSubmit = () => {
    void sendMessage(input);
  };

  return (
    <>
      {isOpen && (
        <section
          id="assistant-panel"
          aria-label="SwiftDrop delivery assistant"
          className="fixed bottom-20 right-4 z-40 flex h-[min(34rem,calc(100vh-6.5rem))] w-[calc(100vw-2rem)] max-w-sm flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl sm:right-6"
          role="dialog"
          aria-modal="false"
          tabIndex={-1}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              setIsOpen(false);
            }
          }}
        >
          <header className="flex items-center justify-between bg-slate-900 px-4 py-3 text-white">
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <h2 className="text-sm font-bold">SwiftDrop Assistant</h2>
                <p className="text-[11px] text-slate-300">Tracking and delivery help</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="rounded-lg p-1.5 text-slate-300 transition-colors hover:bg-slate-800 hover:text-white focus:outline-none focus:ring-2 focus:ring-blue-400"
              aria-label="Close delivery assistant"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          </header>

          <div
            className="flex-1 space-y-3 overflow-y-auto bg-slate-50 p-4"
            aria-live="polite"
          >
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-xs leading-relaxed ${
                    message.role === 'user'
                      ? 'rounded-br-md bg-blue-600 text-white'
                      : message.isError
                        ? 'rounded-bl-md border border-rose-200 bg-rose-50 text-rose-800'
                        : 'rounded-bl-md border border-slate-200 bg-white text-slate-700'
                  }`}
                >
                  <p>{message.content}</p>
                  {message.isError && failedMessage && (
                    <button
                      type="button"
                      onClick={() => void sendMessage(failedMessage, false)}
                      disabled={loading}
                      className="mt-2 inline-flex items-center gap-1 font-bold text-rose-700 underline underline-offset-2 disabled:opacity-50"
                    >
                      <RotateCcw className="h-3 w-3" aria-hidden="true" />
                      Retry
                    </button>
                  )}
                </div>
              </div>
            ))}

            {messages.length === 1 && (
              <div className="flex flex-wrap gap-2 pt-1">
                {SUGGESTED_QUESTIONS.map((question) => (
                  <button
                    key={question}
                    type="button"
                    onClick={() => void sendMessage(question)}
                    disabled={loading}
                    className="rounded-full border border-blue-200 bg-white px-3 py-1.5 text-[11px] font-semibold text-blue-700 transition-colors hover:bg-blue-50 disabled:opacity-50"
                  >
                    {question}
                  </button>
                ))}
              </div>
            )}

            {loading && (
              <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
                <Spinner size="sm" />
                <span>Assistant is thinking...</span>
              </div>
            )}
            <div ref={endOfMessagesRef} />
          </div>

          <div className="border-t border-slate-200 bg-white p-3">
            <div className="flex items-center gap-2">
              <label htmlFor="assistant-message" className="sr-only">
                Message for delivery assistant
              </label>
              <input
                id="assistant-message"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handleSubmit();
                  }
                }}
                placeholder="Ask about tracking or rates..."
                maxLength={MAX_MESSAGE_LENGTH}
                disabled={loading}
                className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3 py-2.5 text-xs text-slate-800 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:bg-slate-50"
              />
              <Button
                type="button"
                size="sm"
                onClick={handleSubmit}
                disabled={!input.trim() || loading}
                aria-label="Send message"
                className="h-10 w-10 rounded-xl px-0"
                leftIcon={<Send className="h-4 w-4" aria-hidden="true" />}
              >
                <span className="sr-only">Send</span>
              </Button>
            </div>
            <p className="mt-1.5 text-[10px] text-slate-400">
              Press Enter to send. Do not share sensitive information.
            </p>
          </div>
        </section>
      )}

      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="fixed bottom-4 right-4 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg shadow-blue-500/30 transition-all hover:bg-blue-700 focus:outline-none focus:ring-4 focus:ring-blue-500/30 sm:right-6"
        aria-label={isOpen ? 'Close delivery assistant' : 'Open delivery assistant'}
        aria-expanded={isOpen}
        aria-controls="assistant-panel"
      >
        {isOpen ? (
          <X className="h-6 w-6" aria-hidden="true" />
        ) : (
          <MessageCircle className="h-6 w-6" aria-hidden="true" />
        )}
      </button>
    </>
  );
}
