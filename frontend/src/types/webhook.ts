export type WebhookDeliveryStatus = 'PENDING' | 'DELIVERED' | 'RETRYING' | 'DEAD_LETTER';

export interface WebhookDeliveryLogItem {
  id: string;
  parcelId: string;
  merchantId: string;
  eventType: string;
  httpStatusCode: number | null;
  retryCount: number;
  deliveryStatus: WebhookDeliveryStatus;
  nextRetryAt: string | null;
  createdAt: string;
}

export interface WebhookLogsPageResponse {
  content: WebhookDeliveryLogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

export interface ManualRetryResponse {
  status: string;
  deliveryLogId: string;
  merchantId: string;
  previousStatus: WebhookDeliveryStatus;
  newStatus: WebhookDeliveryStatus;
  scheduledAt: string;
  message: string;
}

export interface DemoWebhookEvent {
  id: string;
  merchantId: string;
  eventType: string;
  payload: string;
  receivedAt: string;
  verified: boolean;
  verificationStatus: string;
}

export interface DemoWebhookEventsResponse {
  merchantId: string;
  count: number;
  events: DemoWebhookEvent[];
}

export interface MerchantProfileResponse {
  merchantId: string;
  businessName: string;
  registrationNumber: string;
  oauthClientId: string;
  webhookUrl: string | null;
  webhookSecret: string | null;
  newWebhookSecret?: string | null;
}

export interface MerchantWebhookUpdateRequest {
  webhookUrl: string | null;
  regenerateSecret?: boolean;
}
