import { redirect } from 'next/navigation';

/**
 * Route Alias: Redirects /customer/orders to the canonical /customer/history route.
 */
export default function CustomerOrdersAliasPage() {
  redirect('/customer/history');
}
