import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import CourierRunsheetPage from '@/app/courier/runsheet/page';
import { getCourierRunsheet } from '@/services/courierService';
import type { CourierRunsheetResponse } from '@/types/courier';

vi.mock('@/components/layout/AppShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div data-testid="app-shell">{children}</div>,
}));

vi.mock('@/components/courier/CourierShiftToggle', () => ({
  default: () => <div data-testid="shift-toggle">On Duty</div>,
}));

vi.mock('@/components/layout/ToastContext', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
}));

vi.mock('@/services/courierService', () => ({
  getCourierRunsheet: vi.fn(),
  updateParcelStatus: vi.fn(),
}));

const mockedGetCourierRunsheet = vi.mocked(getCourierRunsheet);

const sampleRunsheet: CourierRunsheetResponse = {
  courierId: 'courier-1',
  totalActiveParcels: 5,
  generatedAt: '2026-11-15T06:00:00Z',
  pickups: [
    {
      id: 'p-newer',
      trackingNumber: 'SD-NEWER-002',
      currentStatus: 'ASSIGNED',
      originZoneName: 'Colombo Central Metro',
      originHubLocation: 'No. 120, Baseline Road, Colombo 09',
      destinationZoneName: 'Kandy Central Highlands',
      destinationHubLocation: 'No. 32, William Gopallawa Mawatha, Kandy',
      pickupAddress: 'Colombo 04',
      dropoffAddress: 'Kandy City',
      recipientName: 'Nimal',
      recipientPhone: '+94771111111',
      weightKg: 1.5,
      parcelSize: 'SMALL',
      estimatedFee: 600,
      failureCount: 0,
      updatedAt: '2026-11-15T10:30:00Z',
    },
    {
      id: 'p-older',
      trackingNumber: 'SD-OLDER-001',
      currentStatus: 'ASSIGNED',
      originZoneName: 'Colombo Central Metro',
      originHubLocation: 'No. 120, Baseline Road, Colombo 09',
      destinationZoneName: 'Gampaha Industrial Hub',
      destinationHubLocation: 'No. 45, Colombo Road, Gampaha',
      pickupAddress: 'Colombo 01',
      dropoffAddress: 'Gampaha Town',
      recipientName: 'Kamal',
      recipientPhone: '+94772222222',
      weightKg: 2.0,
      parcelSize: 'MEDIUM',
      estimatedFee: 490,
      failureCount: 0,
      updatedAt: '2026-11-15T09:00:00Z',
    },
  ],
  deliveries: [
    {
      id: 'd-picked',
      trackingNumber: 'SD-PICKED-003',
      currentStatus: 'PICKED_UP',
      originZoneName: 'Colombo Central Metro',
      destinationZoneName: 'Galle Southern Corridor',
      destinationHubLocation: 'No. 90, Wakwella Road, Galle',
      pickupAddress: 'Dehiwala',
      dropoffAddress: 'Galle Fort',
      recipientName: 'Dilshan',
      recipientPhone: '+94773333333',
      weightKg: 3.0,
      parcelSize: 'LARGE',
      estimatedFee: 980,
      failureCount: 0,
      updatedAt: '2026-11-15T09:15:00Z',
    },
    {
      id: 'd-transit',
      trackingNumber: 'SD-TRANSIT-004',
      currentStatus: 'IN_TRANSIT',
      originZoneName: 'Colombo Central Metro',
      destinationZoneName: 'Jaffna Northern Peninsula',
      destinationHubLocation: 'No. 64, KKS Road, Jaffna',
      pickupAddress: 'Peliyagoda',
      dropoffAddress: 'Jaffna Town',
      recipientName: 'Aravindhan',
      recipientPhone: '+94774444444',
      weightKg: 2.5,
      parcelSize: 'MEDIUM',
      estimatedFee: 1150,
      failureCount: 0,
      updatedAt: '2026-11-15T09:20:00Z',
    },
    {
      id: 'd-ofd',
      trackingNumber: 'SD-OFD-005',
      currentStatus: 'OUT_FOR_DELIVERY',
      originZoneName: 'Colombo Central Metro',
      destinationZoneName: 'Colombo Central Metro',
      destinationHubLocation: 'No. 120, Baseline Road, Colombo 09',
      pickupAddress: 'Peliyagoda',
      dropoffAddress: 'Kollupitiya',
      recipientName: 'Sachini',
      recipientPhone: '+94775555555',
      weightKg: 1.0,
      parcelSize: 'SMALL',
      estimatedFee: 470,
      failureCount: 0,
      updatedAt: '2026-11-15T09:30:00Z',
    },
  ],
};

describe('CourierRunsheetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetCourierRunsheet.mockResolvedValue(sampleRunsheet);
  });

  it('renders Active Total badge and FIFO-sorted Assigned parcels first', async () => {
    render(<CourierRunsheetPage />);

    await waitFor(() => {
      expect(screen.getByText('Active Total: 5')).toBeInTheDocument();
    });

    const older = screen.getByText('SD-OLDER-001');
    const newer = screen.getByText('SD-NEWER-002');
    // DOCUMENT_POSITION_FOLLOWING (4) means older comes before newer in DOM order
    expect(older.compareDocumentPosition(newer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('shows stage-aware action button labels across Picked Up, In Transit, and Out for Delivery tabs', async () => {
    render(<CourierRunsheetPage />);

    await waitFor(() => {
      expect(screen.getByText('Active Total: 5')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Picked Up/i }));
    expect(screen.getByText('SD-PICKED-003')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Send to Transit/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /In Transit/i }));
    expect(screen.getByText('SD-TRANSIT-004')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Out for Delivery/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Out for Delivery/i }));
    expect(screen.getByText('SD-OFD-005')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Complete Delivery/i })).toBeInTheDocument();
  });
});
