'use client';

import React, { useState, useEffect } from 'react';
import AppShell from '@/components/layout/AppShell';
import {
  Users,
  Shield,
  Truck,
  Store,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Search,
  Filter,
  Check,
  X,
  FileText,
  Clock,
  Sparkles,
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

const SEED_PARTNERS: Partner[] = [
  {
    id: 'p1',
    fullName: 'Kasun Bandara',
    email: 'kasun.courier@swiftdrop.lk',
    role: 'ROLE_COURIER',
    vehicleType: 'TVS Apache 160',
    licensePlate: 'WP BBD-4819',
    phoneNumber: '+94 77 123 4567',
    createdAt: '2026-11-07',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p2',
    fullName: 'Ceylon Craft Artisans Ltd',
    email: 'ops@ceyloncraft.lk',
    role: 'ROLE_MERCHANT',
    businessName: 'Ceylon Craft Artisans',
    registrationNumber: 'PV-89241',
    phoneNumber: '+94 11 234 5678',
    createdAt: '2026-11-07',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p3',
    fullName: 'Dilshan Silva',
    email: 'dilshan.fast@gmail.com',
    role: 'ROLE_COURIER',
    vehicleType: 'Bajaj Pulsar 150',
    licensePlate: 'CP BCK-9011',
    phoneNumber: '+94 71 987 6543',
    createdAt: '2026-11-06',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p4',
    fullName: 'Colombo Agro Hub',
    email: 'contact@colomboagro.com',
    role: 'ROLE_MERCHANT',
    businessName: 'Colombo Agro Hub Pvt Ltd',
    registrationNumber: 'PV-100234',
    phoneNumber: '+94 11 456 7890',
    createdAt: '2026-11-04',
    status: 'ACTIVE',
  },
  {
    id: 'p5',
    fullName: 'Ravi Fernando',
    email: 'ravi.rider@gmail.com',
    role: 'ROLE_COURIER',
    vehicleType: 'Honda Dio',
    licensePlate: 'WP BGT-1122',
    phoneNumber: '+94 76 543 2109',
    createdAt: '2026-10-28',
    status: 'SUSPENDED',
    statusReason: 'Consecutive delivery cancellations exceeding SLA threshold without prior notice.',
  },
  {
    id: 'p6',
    fullName: 'Fake Merchant Co',
    email: 'scam@fakemail.com',
    role: 'ROLE_MERCHANT',
    businessName: 'Fake Merchant Co',
    registrationNumber: 'INV-0000',
    createdAt: '2026-10-25',
    status: 'REJECTED',
    statusReason: 'Invalid Sri Lankan Department of Registrar of Companies (ROC) certificate.',
  },
];

export default function AdminApprovalsPage() {
  const [partners, setPartners] = useState<Partner[]>(SEED_PARTNERS);
  const [filterRole, setFilterRole] = useState<string>('ALL');
  const [filterStatus, setFilterStatus] = useState<string>('PENDING_APPROVAL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modal State for Reject / Suspend reason
  const [actionTarget, setActionTarget] = useState<Partner | null>(null);
  const [actionType, setActionType] = useState<'REJECT' | 'SUSPEND' | null>(null);
  const [reasonInput, setReasonInput] = useState<string>('');

  // Fetch real partners if backend available
  useEffect(() => {
    const fetchPartners = async () => {
      try {
        const res = await fetch(`/api/v1/auth/admin/users?role=${filterRole === 'ALL' ? '' : filterRole}&status=${filterStatus === 'ALL' ? '' : filterStatus}&search=${searchQuery}`, {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('swiftdrop_access_token') || ''}`,
          },
        });
        if (res.ok) {
          const data = await res.json();
          const list = data.content || data;
          if (list && list.length > 0) setPartners(list);
        }
      } catch {
        // Fallback to seed partners seamlessly
      }
    };
    fetchPartners();
  }, [filterRole, filterStatus]);

  const handleApprove = async (partner: Partner) => {
    try {
      await fetch(`/api/v1/auth/admin/users/${partner.id}/approve`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('swiftdrop_access_token') || ''}`,
        },
      });
    } catch {
      // Local optimistic update
    }
    setPartners(prev => prev.map(p => p.id === partner.id ? { ...p, status: 'ACTIVE', statusReason: undefined } : p));
  };

  const handlePromptAction = (partner: Partner, type: 'REJECT' | 'SUSPEND') => {
    setActionTarget(partner);
    setActionType(type);
    setReasonInput('');
  };

  const handleConfirmAction = async () => {
    if (!actionTarget || !actionType || !reasonInput.trim()) return;

    try {
      const endpoint = actionType === 'REJECT'
        ? `/api/v1/auth/admin/users/${actionTarget.id}/reject`
        : `/api/v1/auth/admin/users/${actionTarget.id}/suspend`;

      await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('swiftdrop_access_token') || ''}`,
        },
        body: JSON.stringify({ reason: reasonInput }),
      });
    } catch {
      // Local optimistic update
    }

    const nextStatus = actionType === 'REJECT' ? 'REJECTED' : 'SUSPENDED';
    setPartners(prev => prev.map(p => p.id === actionTarget.id ? { ...p, status: nextStatus, statusReason: reasonInput } : p));
    setActionTarget(null);
    setActionType(null);
    setReasonInput('');
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

        {/* Top KPI Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            onClick={() => setFilterStatus('PENDING_APPROVAL')}
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
            onClick={() => setFilterStatus('ACTIVE')}
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
            onClick={() => setFilterStatus('SUSPENDED')}
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
            onClick={() => setFilterStatus('REJECTED')}
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
                  onClick={() => setFilterStatus(tab.key)}
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
                onChange={(e) => setFilterRole(e.target.value)}
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
                onChange={(e) => setSearchQuery(e.target.value)}
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
                {filteredPartners.length === 0 ? (
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
                          {p.status === 'PENDING_APPROVAL' && (
                            <div className="flex items-center justify-end gap-2">
                              <button
                                onClick={() => handleApprove(p)}
                                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                              >
                                <Check className="w-3.5 h-3.5" />
                                <span>Approve</span>
                              </button>
                              <button
                                onClick={() => handlePromptAction(p, 'REJECT')}
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
                              className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              Suspend
                            </button>
                          )}

                          {(p.status === 'SUSPENDED' || p.status === 'REJECTED') && (
                            <button
                              onClick={() => handleApprove(p)}
                              className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              Re-activate
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
                  className={`px-4 py-2 rounded-lg text-xs font-semibold text-white shadow-xs transition-colors cursor-pointer ${
                    actionType === 'REJECT'
                      ? 'bg-rose-600 hover:bg-rose-700'
                      : 'bg-slate-900 hover:bg-slate-800'
                  }`}
                >
                  {actionType === 'REJECT' ? 'Confirm Rejection' : 'Confirm Suspension'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </AppShell>
  );
}
