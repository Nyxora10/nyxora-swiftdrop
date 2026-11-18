import { redirect } from 'next/navigation';

/**
 * Safe navigation redirect for existing sidebar link '/admin/reports'
 * routing to Member 5's KPI Analytics Dashboard at '/admin/analytics'.
 */
export default function AdminReportsRedirect() {
  redirect('/admin/analytics');
}

