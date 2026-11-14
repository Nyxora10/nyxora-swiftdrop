import { Radio } from 'lucide-react';
import Badge from '@/components/ui/Badge';
import type { ParcelWebSocketStatus } from '@/lib/websocket';

interface LiveConnectionBadgeProps {
  status: ParcelWebSocketStatus;
  className?: string;
}

export default function LiveConnectionBadge({
  status,
  className = '',
}: LiveConnectionBadgeProps) {
  const isConnected = status === 'CONNECTED';
  const isReconnecting = status === 'RECONNECTING';

  return (
    <Badge
      variant={isConnected ? 'success' : isReconnecting ? 'warning' : 'neutral'}
      size="sm"
      className={className}
      icon={
        <Radio
          className={`h-3 w-3 ${isConnected ? 'animate-pulse' : ''}`}
          aria-hidden="true"
        />
      }
    >
      {isConnected
        ? 'Live Real-Time Updates Active'
        : isReconnecting
          ? 'Reconnecting to Live Updates'
          : 'Live Updates Disconnected'}
    </Badge>
  );
}
