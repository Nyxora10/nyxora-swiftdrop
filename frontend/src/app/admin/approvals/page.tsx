'use client';

import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import AppShell from '@/components/layout/AppShell';
import api from '@/lib/api';
import {
  Truck,
  Store,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Search,
  Check,
  X,
  Clock,
} from 'lucide-react';

interface Partner {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
  role: 'ROLE_MERCHANT' | 'ROLE_COURIER';
  status: 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'REJECTED';
  statusReason?: string;
  createdAt: string;
  businessName?: string;
  registrationNumber?: string;
  vehicleType?: string;
  licensePlate?: string;
}

interface PartnerDetail {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
  role: 'ROLE_MERCHANT' | 'ROLE_COURIER';
  status: 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'REJECTED';
  statusReason?: string;
  authProvider?: string;
  createdAt: string;
  courierProfile?: {
    courierId: string;
    vehicleType?: string;
    licensePlate?: string;
    isAvailable?: boolean;
    activeDeliveryCount?: number;
    assignedZoneId?: number;
  };
  merchantProfile?: {
    merchantId: string;
    businessName?: string;
    registrationNumber?: string;
    webhookUrl?: string;
    oauthClientId?: string;
  };
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as {
      detail?: string;
      message?: string;
      title?: string;
    } | undefined;
    return payload?.detail || payload?.message || payload?.title || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}

export default function AdminApprovalsPage() {
  const [partners, setPartners] = useState<Partner[]>([]);
  const [filterRole, setFilterRole] = useState<string>('ALL');
  const [filterStatus, setFilterStatus] = useState<string>('PENDING_APPROVAL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [busyPartnerId, setBusyPartnerId] = useState<string | null>(null);
  const [detailTarget, setDetailTarget] = useState<Partner | null>(null);
  const [partnerDetail, setPartnerDetail] = useState<PartnerDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // Modal State for Reject / Suspend reason
  const [actionTarget, setActionTarget] = useState<Partner | null>(null);
  const [actionType, setActionType] = useState<'REJECT' | 'SUSPEND' | null>(null);
  const [reasonInput, setReasonInput] = useState<string>('');

  const fetchPartners = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    setLoadError(null);

    try {
      const params = new URLSearchParams({
        role: filterRole === 'ALL' ? '' : filterRole,
        status: filterStatus === 'ALL' ? '' : filterStatus,
        search: searchQuery,
        page: String(currentPage),
        size: '10',
      });
      const { data } = await api.get(`/api/v1/auth/admin/users?${params.toString()}`, {
        signal,
      });
      const list = Array.isArray(data) ? data : data.content;
      const nextPartners = Array.isArray(list) ? list : [];
      setPartners(nextPartners);
      setTotalPages(Array.isArray(data) ? 1 : Math.max(1, data.totalPages || 1));
      setTotalElements(Array.isArray(data) ? nextPartners.length : data.totalElements ?? nextPartners.length);
    } catch (error) {
      if (axios.isCancel(error)) return;
      setPartners([]);
      setTotalPages(1);
      setTotalElements(0);
      setLoadError(getApiErrorMessage(error, 'Unable to load the partner queue.'));
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }, [currentPage, filterRole, filterStatus, searchQuery]);

  const handleStatusFilterChange = (status: string) => {
    setCurrentPage(0);
    setFilterStatus(status);
  };

  const handleRoleFilterChange = (role: string) => {
    setCurrentPage(0);
    setFilterRole(role);
  };

  const handleSearchChange = (query: string) => {
    setCurrentPage(0);
    setSearchQuery(query);
  };

  useEffect(() => {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => {
      void fetchPartners(controller.signal);
    }, 250);

    return () => {
      window.clearTimeout(timeoutId);
      controller.abort();
    };
  }, [fetchPartners]);

  const handleViewDetails = async (partner: Partner) => {
    setDetailTarget(partner);
    setPartnerDetail(null);
    setDetailError(null);
    setDetailLoading(true);

    try {
      const { data } = await api.get<PartnerDetail>(
        `/api/v1/auth/admin/users/${encodeURIComponent(partner.id)}`,
      );
      setPartnerDetail(data);
    } catch (error) {
      setDetailError(getApiErrorMessage(error, 'Unable to load partner details.'));
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetails = () => {
    setDetailTarget(null);
    setPartnerDetail(null);
    setDetailError(null);
  };

  useEffect(() => {
    if (!detailTarget) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDetails();
    };

    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [detailTarget]);

  const handleApprove = async (partner: Partner) => {
    setBusyPartnerId(partner.id);
    setNotice(null);
    try {
      await api.post(`/api/v1/auth/admin/users/${partner.id}/approve`);

      setPartners(prev => prev.map(p => p.id === partner.id ? { ...p, status: 'ACTIVE', statusReason: undefined } : p));
      setNotice({ type: 'success', message: `${partner.fullName} is now active.` });
      await fetchPartners();
    } catch (error) {
      setNotice({
        type: 'error',
        message: getApiErrorMessage(error, 'The approval could not be saved.'),
      });
    } finally {
      setBusyPartnerId(null);
    }
  };

  const handlePromptAction = (partner: Partner, type: 'REJECT' | 'SUSPEND') => {
    setActionTarget(partner);
    setActionType(type);
    setReasonInput('');
  };

  const handleConfirmAction = async () => {
    if (!actionTarget || !actionType || !reasonInput.trim()) return;

    const target = actionTarget;
    const action = actionType;
    const reason = reasonInput.trim();
    setBusyPartnerId(target.id);
    setNotice(null);

    try {
      const endpoint = action === 'REJECT'
        ? `/api/v1/auth/admin/users/${target.id}/reject`
        : `/api/v1/auth/admin/users/${target.id}/suspend`;

      await api.post(endpoint, { reason });

      const nextStatus = action === 'REJECT' ? 'REJECTED' : 'SUSPENDED';
      setPartners(prev => prev.map(p => p.id === target.id ? { ...p, status: nextStatus, statusReason: reason } : p));
      setNotice({
        type: 'success',
        message: `${target.fullName} was ${action === 'REJECT' ? 'rejected' : 'suspended'}.`,
      });
      setActionTarget(null);
      setActionType(null);
      setReasonInput('');
      await fetchPartners();
    } catch (error) {
      setNotice({
        type: 'error',
        message: getApiErrorMessage(error, 'The account decision could not be saved.'),
      });
    } finally {
      setBusyPartnerId(null);
    }
  };

  // Filtered partners
  const filteredPartners = partners.filter(p => {
    const matchesStatus = filterStatus === 'ALL' || p.status === filterStatus;
    const matchesRole = filterRole === 'ALL' || p.role === filterRole;
    const matchesSearch = !searchQuery ||
      p.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (p.businessName && p.businessName.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (p.licensePlate && p.licensePlate.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesStatus && matchesRole && matchesSearch;
  });

  const pendingCount = partners.filter(p => p.status === 'PENDING_APPROVAL').length;
  const activeCount = partners.filter(p => p.status === 'ACTIVE').length;
  const suspendedCount = partners.filter(p => p.status === 'SUSPENDED').length;
  const rejectedCount = partners.filter(p => p.status === 'REJECTED').length;

  return (
    <AppShell>
      <div className="space-y-6">

        {/* Page Top Header */}
        <div className="pb-4 border-b border-slate-200">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
                Partner Review &amp; Approval Queue
              </h1>
              <p className="text-sm text-slate-500 mt-1">
                Review and audit merchant business registrations and courier driver licensing applications.
              </p>
            </div>
          </div>
        </div>

        {loadError && (
          <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
            <span className="font-semibold">Queue unavailable:</span> {loadError}
          </div>
        )}

        {notice && (
          <div
            role="status"
            className={`rounded-xl border px-4 py-3 text-sm ${
              notice.type === 'success'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                : 'border-amber-200 bg-amber-50 text-amber-900'
            }`}
          >
            {notice.message}
          </div>
        )}

        {/* Top KPI Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            onClick={() => handleStatusFilterChange('PENDING_APPROVAL')}
            className={`p-4 rounded-xl border transition-all cursor-pointer ${
              filterStatus === 'PENDING_APPROVAL'
                ? 'bg-slate-50/80 border-slate-900 ring-1 ring-slate-900 shadow-xs'
                : 'bg-white border-slate-200 hover:border-slate-300 shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Pending Review</span>
              <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="text-2xl font-bold text-slate-900">{pendingCount} Applicants</div>
            <div className="text-xs text-slate-500 mt-1">Requires admin sign-off</div>
          </div>

          <div
            onClick={() => handleStatusFilterChange('ACTIVE')}
            className={`p-4 rounded-xl border transition-all cursor-pointer ${
              filterStatus === 'ACTIVE'
                ? 'bg-slate-50/80 border-slate-900 ring-1 ring-slate-900 shadow-xs'
                : 'bg-white border-slate-200 hover:border-slate-300 shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Active Partners</span>
              <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div className="text-2xl font-bold text-slate-900">{activeCount} Verified</div>
            <div className="text-xs text-slate-500 mt-1">Authorized for bookings</div>
          </div>

          <div
            onClick={() => handleStatusFilterChange('SUSPENDED')}
            className={`p-4 rounded-xl border transition-all cursor-pointer ${
              filterStatus === 'SUSPENDED'
                ? 'bg-slate-50/80 border-slate-900 ring-1 ring-slate-900 shadow-xs'
                : 'bg-white border-slate-200 hover:border-slate-300 shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Suspended</span>
              <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                <AlertTriangle className="w-4 h-4" />
              </div>
            </div>
            <div className="text-2xl font-bold text-slate-900">{suspendedCount} Accounts</div>
            <div className="text-xs text-slate-500 mt-1">Temporary hold</div>
          </div>

          <div
            onClick={() => handleStatusFilterChange('REJECTED')}
            className={`p-4 rounded-xl border transition-all cursor-pointer ${
              filterStatus === 'REJECTED'
                ? 'bg-slate-50/80 border-slate-900 ring-1 ring-slate-900 shadow-xs'
                : 'bg-white border-slate-200 hover:border-slate-300 shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Rejected</span>
              <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                <XCircle className="w-4 h-4" />
              </div>
            </div>
            <div className="text-2xl font-bold text-slate-900">{rejectedCount} Applications</div>
            <div className="text-xs text-slate-500 mt-1">Audit trail logged</div>
          </div>
        </div>

        {/* Filter Controls Bar */}
        <div className="bg-white border border-slate-200 rounded-xl p-3 shadow-xs flex flex-col md:flex-row md:items-center md:justify-between gap-3">
          
          {/* Status Filter Buttons */}
          <div className="flex gap-1.5 flex-wrap items-center">
            {[
              { key: 'PENDING_APPROVAL', label: 'Pending', count: pendingCount },
              { key: 'ACTIVE', label: 'Active', count: activeCount },
              { key: 'SUSPENDED', label: 'Suspended', count: suspendedCount },
              { key: 'REJECTED', label: 'Rejected', count: rejectedCount },
              { key: 'ALL', label: 'All', count: partners.length },
            ].map(tab => {
              const isActive = filterStatus === tab.key;
              return (
                <button
                  key={tab.key}
                  onClick={() => handleStatusFilterChange(tab.key)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer flex items-center gap-1.5 ${
                    isActive
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                  }`}
                >
                  <span>{tab.label}</span>
                  <span className={`text-[11px] px-1.5 py-0.2 rounded-md ${
                    isActive ? 'bg-slate-800 text-slate-300' : 'bg-slate-100 text-slate-500'
                  }`}>
                    {tab.count}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Role Filter & Search */}
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <select
                value={filterRole}
                onChange={(e) => handleRoleFilterChange(e.target.value)}
                className="bg-white border border-slate-200 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-700 focus:outline-none focus:ring-1 focus:ring-slate-900 cursor-pointer"
              >
                <option value="ALL">All Roles</option>
                <option value="ROLE_MERCHANT">Merchants Only</option>
                <option value="ROLE_COURIER">Couriers Only</option>
              </select>
            </div>

            <div className="relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                placeholder="Search partner or vehicle..."
                className="bg-white border border-slate-200 rounded-lg pl-8.5 pr-3 py-1.5 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900 w-48 sm:w-56"
              />
            </div>
          </div>

        </div>

        {/* Partners Table */}
        <div className="bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="bg-slate-50 text-slate-500 uppercase text-[11px] tracking-wider border-b border-slate-200 font-semibold">
                <tr>
                  <th className="px-5 py-3">Partner Entity</th>
                  <th className="px-5 py-3">Role</th>
                  <th className="px-5 py-3">Business / Fleet Specs</th>
                  <th className="px-5 py-3">Application Date</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3 text-right">Decision Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {isLoading ? (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-slate-400">
                      Loading partner queue...
                    </td>
                  </tr>
                ) : filteredPartners.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-slate-400">
                      No partners found matching the selected filter criteria.
                    </td>
                  </tr>
                ) : (
                  filteredPartners.map(p => {
                    const isMerchant = p.role === 'ROLE_MERCHANT';
                    return (
                      <tr key={p.id} className="hover:bg-slate-50/70 transition-colors">
                        
                        {/* Name & Contact */}
                        <td className="px-5 py-3.5">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 border border-slate-200 flex items-center justify-center shrink-0">
                              {isMerchant ? <Store className="w-4 h-4" /> : <Truck className="w-4 h-4" />}
                            </div>
                            <div>
                              <div className="font-semibold text-slate-900 leading-tight">{p.fullName}</div>
                              <div className="text-xs text-slate-500 mt-0.5">{p.email}</div>
                              {p.phoneNumber && (
                                <div className="text-[11px] text-slate-400 font-mono">{p.phoneNumber}</div>
                              )}
                            </div>
                          </div>
                        </td>

                        {/* Role Badge */}
                        <td className="px-5 py-3.5">
                          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium bg-slate-100 text-slate-700 border border-slate-200">
                            {isMerchant ? 'Merchant' : 'Courier'}
                          </span>
                        </td>

                        {/* Specs */}
                        <td className="px-5 py-3.5">
                          {isMerchant ? (
                            <div>
                              <div className="font-medium text-slate-800">{p.businessName || 'Merchant Partner'}</div>
                              <div className="text-xs text-slate-500 font-mono">ROC: {p.registrationNumber || 'N/A'}</div>
                            </div>
                          ) : (
                            <div>
                              <div className="font-medium text-slate-800">{p.vehicleType || 'Vehicle'}</div>
                              <div className="text-xs text-slate-500 font-mono">Plate: {p.licensePlate || 'N/A'}</div>
                            </div>
                          )}
                        </td>

                        {/* Date */}
                        <td className="px-5 py-3.5 text-xs text-slate-500 font-mono">
                          {p.createdAt}
                        </td>

                        {/* Status - Color reserved for this essential part */}
                        <td className="px-5 py-3.5">
                          <div>
                            {p.status === 'ACTIVE' && (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/80">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                                Active
                              </span>
                            )}
                            {p.status === 'PENDING_APPROVAL' && (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200/80">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                                Pending Approval
                              </span>
                            )}
                            {p.status === 'SUSPENDED' && (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                                Suspended
                              </span>
                            )}
                            {p.status === 'REJECTED' && (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-50 text-rose-700 border border-rose-200/80">
                                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                                Rejected
                              </span>
                            )}
                            {p.statusReason && (
                              <div className="text-[11px] text-slate-500 mt-1 max-w-xs leading-snug">
                                Reason: {p.statusReason}
                              </div>
                            )}
                          </div>
                        </td>

                        {/* Action Buttons */}
                        <td className="px-5 py-3.5 text-right">
                          <button
                            onClick={() => void handleViewDetails(p)}
                            disabled={busyPartnerId !== null}
                            className="mb-2 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                          >
                            View details
                          </button>

                          {p.status === 'PENDING_APPROVAL' && (
                            <div className="flex items-center justify-end gap-2">
                              <button
                                onClick={() => handleApprove(p)}
                                disabled={busyPartnerId !== null}
                                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                              >
                                <Check className="w-3.5 h-3.5" />
                                <span>{busyPartnerId === p.id ? 'Saving...' : 'Approve'}</span>
                              </button>
                              <button
                                onClick={() => handlePromptAction(p, 'REJECT')}
                                disabled={busyPartnerId !== null}
                                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-rose-600 hover:bg-rose-50 border border-slate-200 transition-colors cursor-pointer"
                              >
                                <X className="w-3.5 h-3.5" />
                                <span>Reject</span>
                              </button>
                            </div>
                          )}

                          {p.status === 'ACTIVE' && (
                            <button
                              onClick={() => handlePromptAction(p, 'SUSPEND')}
                              disabled={busyPartnerId !== null}
                              className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              Suspend
                            </button>
                          )}

                          {(p.status === 'SUSPENDED' || p.status === 'REJECTED') && (
                            <button
                              onClick={() => handleApprove(p)}
                              disabled={busyPartnerId !== null}
                              className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              {busyPartnerId === p.id ? 'Saving...' : 'Re-activate'}
                            </button>
                          )}
                        </td>

                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {totalPages > 1 && !isLoading && (
          <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs text-slate-500 sm:flex-row sm:items-center sm:justify-between">
            <span>
              Page {currentPage + 1} of {totalPages} · {totalElements} total partners
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.max(0, page - 1))}
                disabled={currentPage === 0}
                className="rounded-lg border border-slate-200 px-3 py-1.5 font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.min(totalPages - 1, page + 1))}
                disabled={currentPage >= totalPages - 1}
                className="rounded-lg border border-slate-200 px-3 py-1.5 font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        )}

        {/* Modal: Partner Detail Inspection */}
        {detailTarget && (
          <div
            className="fixed inset-0 z-40 flex items-center justify-end bg-slate-900/50 backdrop-blur-xs"
            role="dialog"
            aria-modal="true"
            aria-labelledby="partner-detail-title"
            onMouseDown={(event) => {
              if (event.target === event.currentTarget) closeDetails();
            }}
          >
            <div className="h-full w-full max-w-xl overflow-y-auto bg-white p-6 shadow-2xl">
              <div className="flex items-start justify-between border-b border-slate-100 pb-4">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Partner inspection</p>
                  <h2 id="partner-detail-title" className="mt-1 text-xl font-bold text-slate-900">
                    {detailTarget.fullName}
                  </h2>
                  <p className="mt-1 text-sm text-slate-500">{detailTarget.email}</p>
                </div>
                <button
                  type="button"
                  onClick={closeDetails}
                  aria-label="Close partner details"
                  className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {detailLoading && (
                <div className="py-12 text-center text-sm text-slate-500">Loading partner details...</div>
              )}

              {detailError && (
                <div role="alert" className="mt-6 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                  <p>{detailError}</p>
                  <button
                    type="button"
                    onClick={() => void handleViewDetails(detailTarget)}
                    disabled={detailLoading}
                    className="mt-3 rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-semibold text-rose-800 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Retry loading details
                  </button>
                </div>
              )}

              {!detailLoading && !detailError && !partnerDetail && (
                <div className="py-12 text-center text-sm text-slate-500">No partner details were returned.</div>
              )}

              {!detailLoading && !detailError && partnerDetail && (
                <div className="mt-6 space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Role</p>
                      <p className="mt-1 text-sm font-semibold text-slate-900">
                        {partnerDetail.role === 'ROLE_MERCHANT' ? 'Merchant' : 'Courier'}
                      </p>
                    </div>
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Status</p>
                      <p className="mt-1 text-sm font-semibold text-slate-900">{partnerDetail.status.replaceAll('_', ' ')}</p>
                    </div>
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Phone</p>
                      <p className="mt-1 text-sm font-semibold text-slate-900">{partnerDetail.phoneNumber || 'Not provided'}</p>
                    </div>
                    <div className="rounded-xl border border-slate-200 p-3">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Created</p>
                      <p className="mt-1 text-sm font-semibold text-slate-900">{partnerDetail.createdAt}</p>
                    </div>
                  </div>

                  {partnerDetail.statusReason && (
                    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-amber-700">Status reason</p>
                      <p className="mt-1 text-sm leading-relaxed text-amber-950">{partnerDetail.statusReason}</p>
                    </div>
                  )}

                  {partnerDetail.merchantProfile && (
                    <div className="rounded-xl border border-slate-200 p-4">
                      <h3 className="text-sm font-bold text-slate-900">Merchant registration</h3>
                      <dl className="mt-3 space-y-2 text-sm">
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Business name</dt><dd className="text-right font-medium text-slate-900">{partnerDetail.merchantProfile.businessName || 'Not provided'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Registration number</dt><dd className="text-right font-mono text-slate-900">{partnerDetail.merchantProfile.registrationNumber || 'Not provided'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Webhook URL</dt><dd className="max-w-[15rem] truncate text-right font-mono text-slate-900">{partnerDetail.merchantProfile.webhookUrl || 'Not configured'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">OAuth client ID</dt><dd className="max-w-[15rem] truncate text-right font-mono text-slate-900">{partnerDetail.merchantProfile.oauthClientId || 'Not configured'}</dd></div>
                      </dl>
                    </div>
                  )}

                  {partnerDetail.courierProfile && (
                    <div className="rounded-xl border border-slate-200 p-4">
                      <h3 className="text-sm font-bold text-slate-900">Courier verification</h3>
                      <dl className="mt-3 space-y-2 text-sm">
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Vehicle</dt><dd className="text-right font-medium text-slate-900">{partnerDetail.courierProfile.vehicleType || 'Not provided'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">License plate</dt><dd className="text-right font-mono text-slate-900">{partnerDetail.courierProfile.licensePlate || 'Not provided'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Availability</dt><dd className="text-right font-medium text-slate-900">{partnerDetail.courierProfile.isAvailable ? 'Available' : 'Unavailable'}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Active deliveries</dt><dd className="text-right font-medium text-slate-900">{partnerDetail.courierProfile.activeDeliveryCount ?? 0}</dd></div>
                        <div className="flex justify-between gap-4"><dt className="text-slate-500">Assigned zone</dt><dd className="text-right font-medium text-slate-900">{partnerDetail.courierProfile.assignedZoneId ?? 'Not assigned'}</dd></div>
                      </dl>
                    </div>
                  )}

                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
                    Authentication provider: <span className="font-medium text-slate-700">{partnerDetail.authProvider || 'Unknown'}</span>
                  </div>

                  <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
                    {partnerDetail.status === 'PENDING_APPROVAL' && (
                      <>
                        <button
                          type="button"
                          onClick={() => { closeDetails(); void handleApprove(detailTarget); }}
                          disabled={busyPartnerId !== null}
                          className="inline-flex items-center gap-1 rounded-lg bg-slate-900 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <Check className="h-3.5 w-3.5" />
                          Approve
                        </button>
                        <button
                          type="button"
                          onClick={() => { handlePromptAction(detailTarget, 'REJECT'); closeDetails(); }}
                          disabled={busyPartnerId !== null}
                          className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-600 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <X className="h-3.5 w-3.5" />
                          Reject
                        </button>
                      </>
                    )}

                    {partnerDetail.status === 'ACTIVE' && (
                      <button
                        type="button"
                        onClick={() => { handlePromptAction(detailTarget, 'SUSPEND'); closeDetails(); }}
                        disabled={busyPartnerId !== null}
                        className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        Suspend account
                      </button>
                    )}

                    {(partnerDetail.status === 'SUSPENDED' || partnerDetail.status === 'REJECTED') && (
                      <button
                        type="button"
                        onClick={() => { closeDetails(); void handleApprove(detailTarget); }}
                        disabled={busyPartnerId !== null}
                        className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        Re-activate account
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Modal: Mandatory Justification */}
        {actionTarget && actionType && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
            <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4">
              <div className="flex justify-between items-center border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-slate-900">
                  {actionType === 'REJECT' ? 'Mandatory Rejection Justification' : 'Mandatory Suspension Justification'}
                </h3>
                <button
                  onClick={() => { setActionTarget(null); setActionType(null); }}
                  className="text-slate-400 hover:text-slate-600"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div>
                <p className="text-xs text-slate-600 mb-2">
                  Target Partner: <strong className="text-slate-900">{actionTarget.fullName}</strong> ({actionTarget.email})
                </p>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                  Audit Reason (Recorded in Database &amp; Emailed)
                </label>
                <textarea
                  rows={3}
                  value={reasonInput}
                  onChange={(e) => setReasonInput(e.target.value)}
                  placeholder={
                    actionType === 'REJECT'
                      ? 'e.g., Incomplete business registration certificate or invalid driving license.'
                      : 'e.g., Exceeded maximum allowed delivery cancellation threshold.'
                  }
                  required
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl p-3 text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => { setActionTarget(null); setActionType(null); }}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmAction}
                  disabled={busyPartnerId !== null || !reasonInput.trim()}
                  className={`px-4 py-2 rounded-lg text-xs font-semibold text-white shadow-xs transition-colors cursor-pointer ${
                    actionType === 'REJECT'
                      ? 'bg-rose-600 hover:bg-rose-700'
                      : 'bg-slate-900 hover:bg-slate-800'
                  }`}
                >
                  {busyPartnerId !== null
                    ? 'Saving...'
                    : actionType === 'REJECT' ? 'Confirm Rejection' : 'Confirm Suspension'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </AppShell>
  );
}
