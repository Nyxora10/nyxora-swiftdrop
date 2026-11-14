import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/lib/api';
import MerchantDashboardPage from '../dashboard/page';
import MerchantOrdersPage from '../orders/page';
import MerchantBulkUploadPage from '../bulk-upload/page';
import MerchantWebhooksPage from '../webhooks/page';

vi.mock('@/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock('@/components/layout/AppShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);

const profile = {
  merchantId: 'e1000000-0000-4000-8000-000000000001',
  businessName: 'Daraz Sri Lanka PVT LTD',
  registrationNumber: 'PV-123456',
  oauthClientId: 'mch_daraz_client_01',
  webhookUrl: 'https://store.lk/hooks',
  webhookSecret: 'whsec_****2026',
};

const order = (n: number, overrides = {}) => ({
  parcelId: `p-${n}`,
  trackingNumber: `SD-20261114-00000${n}`,
  recipientName: `Recipient ${n}`,
  recipientPhone: '0771234567',
  dropoffAddress: 'Kandy',
  destinationZoneName: 'Gampaha',
  weightKg: 2,
  parcelSize: 'MEDIUM',
  estimatedFee: 697.2,
  discountAmount: 91.5,
  currentStatus: 'CREATED',
  createdAt: '2026-11-14T05:36:37Z',
  ...overrides,
});

const history = (orders: ReturnType<typeof order>[], totalElements = orders.length, totalPages = 1) => ({
  data: {
    totalOrders: 37,
    inTransitCount: 4,
    deliveredCount: 30,
    failedCount: 1,
    totalSavings: 1830.5,
    orders: { content: orders, pageNumber: 0, pageSize: 10, totalElements, totalPages },
  },
});

function routeGets(routes: Record<string, () => unknown>) {
  get.mockImplementation((url: string) => {
    const handler = routes[url];
    return handler ? Promise.resolve(handler()) : Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  put.mockReset();
});

describe('Merchant dashboard', () => {
  it('shows server lifetime figures, real savings and the latest orders', async () => {
    routeGets({
      '/api/v1/auth/merchant/profile': () => ({ data: profile }),
      '/api/v1/parcels/merchant/orders': () => history([order(1), order(2, { discountAmount: null })], 37, 4),
    });

    render(<MerchantDashboardPage />);

    expect(await screen.findByText('SD-20261114-000001')).toBeInTheDocument();
    expect(screen.getByText('Daraz Sri Lanka PVT LTD', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('LKR 1,830.50')).toBeInTheDocument();
    expect(screen.getByText('81%')).toBeInTheDocument(); // 30 of 37 delivered
    expect(screen.getAllByText('Saved LKR 91.50')).toHaveLength(1); // no badge when discount is unknown
    expect(screen.getByText('Showing the latest 2 of 37 orders.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /View all orders/ })).toHaveAttribute('href', '/merchant/orders');
    expect(get).toHaveBeenCalledWith('/api/v1/parcels/merchant/orders', { params: { page: 0, size: 10 } });
  });

  it('keeps the orders visible when only the profile fails', async () => {
    get.mockImplementation((url: string) =>
      url === '/api/v1/auth/merchant/profile'
        ? Promise.reject({ response: { data: { detail: 'Profile service down' } } })
        : Promise.resolve(history([order(1)])),
    );

    render(<MerchantDashboardPage />);

    expect(await screen.findByText('Profile service down')).toBeInTheDocument();
    expect(await screen.findByText('SD-20261114-000001')).toBeInTheDocument();
  });
});

describe('Merchant orders page', () => {
  it('pages and filters on the server', async () => {
    routeGets({ '/api/v1/parcels/merchant/orders': () => history([order(1), order(2)], 60, 3) });

    render(<MerchantOrdersPage />);
    expect(await screen.findByText('Showing 1–25 of 60')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith('/api/v1/parcels/merchant/orders', { params: { page: 1, size: 25 } }),
    );

    fireEvent.click(screen.getByRole('tab', { name: 'Delivered' }));
    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith('/api/v1/parcels/merchant/orders', {
        params: { page: 0, size: 25, status: 'DELIVERED' },
      }),
    );
  });

  it('searches within the loaded page', async () => {
    routeGets({ '/api/v1/parcels/merchant/orders': () => history([order(1), order(2)]) });
    render(<MerchantOrdersPage />);
    await screen.findByText('SD-20261114-000002');

    fireEvent.change(screen.getByLabelText('Search this page'), { target: { value: 'recipient 1' } });
    expect(screen.queryByText('SD-20261114-000002')).not.toBeInTheDocument();
    expect(screen.getByText('SD-20261114-000001')).toBeInTheDocument();
  });
});

function uploadFile(name: string, content: string) {
  const input = document.getElementById('bulk-file-input') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [new File([content], name, { type: 'text/plain' })] } });
}

const HEADER =
  'originZoneId,destinationZoneId,pickupAddress,dropoffAddress,recipientName,recipientPhone,weightKg,parcelSize,merchantOrderReference';

describe('Bulk upload page', () => {
  it('previews rows, sends only valid ones and reports a partial result honestly', async () => {
    post.mockResolvedValue({
      data: {
        totalReceived: 1,
        successfulCount: 1,
        failedCount: 0,
        results: [{ rowNumber: 1, trackingNumber: 'SD-20261114-BULK01', estimatedFee: 550.5, status: 'SUCCESS', errors: [] }],
      },
    });
    render(<MerchantBulkUploadPage />);

    uploadFile('orders.csv', `${HEADER}\n1,2,W,"Kandy, Central",Kasun,0771234567,2,SMALL,R-1\n1,2,W,Galle,Nimal,12345,2,SMALL,R-2\n`);

    expect(await screen.findByText('Recipient phone must be 9 to 15 digits, optionally starting with +')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Create 1 Order/ }));

    expect(await screen.findByText('Some orders were not created')).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith('/api/v1/parcels/bulk', {
      orders: [expect.objectContaining({ merchantOrderReference: 'R-1', dropoffAddress: 'Kandy, Central' })],
    });
    const rows = screen.getAllByRole('row');
    expect(within(rows[1]).getByText('SD-20261114-BULK01')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Failed')).toBeInTheDocument();
  });

  it('says no orders were created when every row fails on the server', async () => {
    post.mockResolvedValue({
      data: {
        totalReceived: 1,
        successfulCount: 0,
        failedCount: 1,
        results: [{ rowNumber: 1, trackingNumber: null, estimatedFee: null, status: 'FAILED', errors: ['Zone 99 not found.'] }],
      },
    });
    render(<MerchantBulkUploadPage />);
    uploadFile('orders.csv', `${HEADER}\n1,99,W,Kandy,Kasun,0771234567,2,SMALL,R-1\n`);

    fireEvent.click(await screen.findByRole('button', { name: /Create 1 Order/ }));
    expect(await screen.findByText('No orders were created')).toBeInTheDocument();
    expect(screen.getByText('Zone 99 not found.')).toBeInTheDocument();
  });

  it('warns when rows past the limit are left out', async () => {
    const lines = Array.from({ length: 103 }, (_, i) => `1,2,W,Kandy,K,0771234567,1,SMALL,R-${i}`);
    render(<MerchantBulkUploadPage />);
    uploadFile('big.csv', `${HEADER}\n${lines.join('\n')}`);
    expect(await screen.findByText(/3 more rows were left/)).toBeInTheDocument();
  });

  it('shows file problems on the page instead of a browser alert', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    render(<MerchantBulkUploadPage />);
    uploadFile('orders.json', '{oops');
    expect(await screen.findByRole('alert')).toHaveTextContent('The file is not valid JSON.');
    expect(alertSpy).not.toHaveBeenCalled();
  });
});

describe('Webhooks page', () => {
  it('saves a new URL and reveals a regenerated secret once', async () => {
    routeGets({
      '/api/v1/auth/merchant/profile': () => ({ data: profile }),
      '/api/v1/webhooks/demo-events': () => ({ data: { merchantId: profile.merchantId, count: 0, events: [] } }),
    });
    put.mockImplementation((_url: string, body: unknown) =>
      Promise.resolve({
        data: (body as { regenerateSecret?: boolean }).regenerateSecret
          ? { ...profile, webhookSecret: 'whsec_****beef', newWebhookSecret: 'whsec_new_plaintext' }
          : { ...profile, webhookUrl: 'https://shop.lk/swiftdrop' },
      }),
    );

    render(<MerchantWebhooksPage />);
    const urlInput = await screen.findByLabelText('Webhook URL');
    await waitFor(() => expect(urlInput).toHaveValue('https://store.lk/hooks'));

    fireEvent.change(urlInput, { target: { value: 'not a url' } });
    expect(screen.getByText('Enter a full address starting with https:// or http://')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save URL' })).toBeDisabled();

    fireEvent.change(urlInput, { target: { value: 'https://shop.lk/swiftdrop' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save URL' }));
    expect(await screen.findByText(/Webhook URL saved/)).toBeInTheDocument();
    expect(put).toHaveBeenCalledWith('/api/v1/auth/merchant/webhook', { webhookUrl: 'https://shop.lk/swiftdrop' });

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate secret' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Regenerate secret' })); // confirm step
    expect(await screen.findByDisplayValue('whsec_new_plaintext')).toBeInTheDocument();
    expect(screen.getByText('whsec_****beef')).toBeInTheDocument();
  });

  it('explains the unavailable event log in plain words', async () => {
    get.mockImplementation((url: string) =>
      url === '/api/v1/auth/merchant/profile'
        ? Promise.resolve({ data: profile })
        : Promise.reject({ response: { status: 503, data: { error: 'AUTH_MAPPING_UNAVAILABLE' } } }),
    );
    render(<MerchantWebhooksPage />);
    expect(await screen.findByText(/The event log is not available yet/)).toBeInTheDocument();
  });

  it('lists received events with their payload', async () => {
    routeGets({
      '/api/v1/auth/merchant/profile': () => ({ data: profile }),
      '/api/v1/webhooks/demo-events': () => ({
        data: {
          merchantId: profile.merchantId,
          count: 1,
          events: [
            {
              id: 'ev-1',
              merchantId: profile.merchantId,
              eventType: 'parcel.status.updated',
              payload: '{"trackingNumber":"SD-20261114-000001","status":"DELIVERED"}',
              receivedAt: '2026-11-14T08:00:00Z',
              verified: true,
              verificationStatus: 'VERIFIED_HMAC_SHA256',
            },
          ],
        },
      }),
    });
    render(<MerchantWebhooksPage />);
    fireEvent.click(await screen.findByRole('button', { name: /parcel.status.updated/ }));
    expect(screen.getByText(/"status": "DELIVERED"/)).toBeInTheDocument();
  });
});
