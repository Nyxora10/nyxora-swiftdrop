/**
 * SwiftDrop KPI Analytics API Client Service (Member 5: Tasks 1.10 & 6.4)
 * Communicates with backend parcel-service reporting endpoints via API Gateway.
 */

import { api } from '@/lib/api';
import { OperationalSummaryResponse } from '@/types/analytics';

/**
 * Retrieves aggregated operational delivery metrics, daily time series,
 * failure reason breakdown, and zone performance.
 *
 * @param from Optional start date in ISO format (YYYY-MM-DD), inclusive. Defaults to 30 days prior.
 * @param to Optional end date in ISO format (YYYY-MM-DD), inclusive. Defaults to today.
 */
export async function getOperationalSummary(
  from?: string,
  to?: string
): Promise<OperationalSummaryResponse> {
  const params: Record<string, string> = {};
  if (from?.trim()) {
    params.from = from.trim();
  }
  if (to?.trim()) {
    params.to = to.trim();
  }

  const response = await api.get<OperationalSummaryResponse>('/api/v1/parcels/reports/summary', {
    params,
  });
  return response.data;
}

