import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import TrackingMilestoneStepper from '@/components/tracking/TrackingMilestoneStepper';

describe('TrackingMilestoneStepper', () => {
  it('marks the current milestone and preceding milestones as active', () => {
    render(<TrackingMilestoneStepper currentStatus="IN_TRANSIT" />);

    expect(screen.getByText('Created')).toHaveClass('text-slate-900');
    expect(screen.getByText('Picked up')).toHaveClass('text-slate-900');
    expect(screen.getByText('In transit')).toHaveClass('text-slate-900');
    expect(screen.getByText('Out for delivery')).toHaveClass('text-slate-400');
    expect(screen.getByText('Delivered')).toHaveClass('text-slate-400');
  });

  it('shows all milestones as active when the parcel is delivered', () => {
    render(<TrackingMilestoneStepper currentStatus="DELIVERED" />);

    expect(screen.getByText('Created')).toHaveClass('text-slate-900');
    expect(screen.getByText('Picked up')).toHaveClass('text-slate-900');
    expect(screen.getByText('In transit')).toHaveClass('text-slate-900');
    expect(screen.getByText('Out for delivery')).toHaveClass('text-slate-900');
    expect(screen.getByText('Delivered')).toHaveClass('text-slate-900');
  });

  it('reports failed delivery separately', () => {
    render(<TrackingMilestoneStepper currentStatus="FAILED" />);

    expect(screen.getByText('Delivery attempt failed')).toBeInTheDocument();
    expect(screen.getByLabelText('Parcel progress: FAILED')).toBeInTheDocument();
  });
});
