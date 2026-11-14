import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiDeliveryAssistantWidget from '@/components/AiDeliveryAssistantWidget';
import { sendAssistantMessage } from '@/services/aiService';

vi.mock('@/services/aiService', () => ({
  sendAssistantMessage: vi.fn(),
}));

const mockedSendAssistantMessage = vi.mocked(sendAssistantMessage);

describe('AiDeliveryAssistantWidget', () => {
  beforeEach(() => {
    mockedSendAssistantMessage.mockReset();
  });

  it('opens and closes the chat panel', () => {
    render(<AiDeliveryAssistantWidget />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Open delivery assistant' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Where is my parcel?')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Close delivery assistant' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('submits a message and reuses the backend conversation id', async () => {
    mockedSendAssistantMessage
      .mockResolvedValueOnce({
        reply: 'Your parcel is in transit.',
        conversationId: 'conversation-123',
      })
      .mockResolvedValueOnce({
        reply: 'The estimated rate is LKR 450.',
        conversationId: 'conversation-123',
      });

    render(<AiDeliveryAssistantWidget />);
    fireEvent.click(screen.getByRole('button', { name: 'Open delivery assistant' }));

    const input = screen.getByRole('textbox', { name: 'Message for delivery assistant' });
    fireEvent.change(input, { target: { value: 'Track SD-20261107-7K9P2X' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => {
      expect(screen.getByText('Your parcel is in transit.')).toBeInTheDocument();
    });

    fireEvent.change(input, { target: { value: 'What is the shipping rate?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send message' }));

    await waitFor(() => {
      expect(screen.getByText('The estimated rate is LKR 450.')).toBeInTheDocument();
    });

    expect(mockedSendAssistantMessage).toHaveBeenNthCalledWith(1, {
      message: 'Track SD-20261107-7K9P2X',
      conversationId: null,
    });
    expect(mockedSendAssistantMessage).toHaveBeenNthCalledWith(2, {
      message: 'What is the shipping rate?',
      conversationId: 'conversation-123',
    });
  });

  it('shows loading and supports retry after an error', async () => {
    let resolveRequest!: (value: { reply: string; conversationId: string }) => void;
    mockedSendAssistantMessage.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRequest = resolve;
      })
    );

    render(<AiDeliveryAssistantWidget />);
    fireEvent.click(screen.getByRole('button', { name: 'Open delivery assistant' }));

    const input = screen.getByRole('textbox', { name: 'Message for delivery assistant' });
    fireEvent.change(input, { target: { value: 'Where is SD-20261107-7K9P2X?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send message' }));

    expect(screen.getByText('Assistant is thinking...')).toBeInTheDocument();
    resolveRequest({
      reply: 'The parcel is out for delivery.',
      conversationId: 'conversation-456',
    });
    await waitFor(() => {
      expect(screen.getByText('The parcel is out for delivery.')).toBeInTheDocument();
    });

    mockedSendAssistantMessage.mockRejectedValueOnce(new Error('network failure'));
    fireEvent.change(input, { target: { value: 'Tell me more' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send message' }));

    await waitFor(() => {
      expect(screen.getByText(/could not reach the delivery assistant/i)).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(mockedSendAssistantMessage).toHaveBeenLastCalledWith({
      message: 'Tell me more',
      conversationId: 'conversation-456',
    });
  });
});
