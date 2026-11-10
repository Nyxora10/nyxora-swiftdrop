'use client';

import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import AppShell from '@/components/layout/AppShell';
import api from '@/lib/api';
import {
  MapPin,
  Layers,
  Zap,
  Plus,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  Sliders,
  Calculator,
  Scale,
  Box,
  Percent,
  X,
  RefreshCw,
} from 'lucide-react';

interface DeliveryZone {
  id: number;
  zoneCode: string;
  zoneName: string;
  district: string;
  baseSurcharge: number;
  isActive: boolean;
}

interface PricingRule {
  id: number;
  originZone: DeliveryZone;
  destinationZone: DeliveryZone;
  baseFee: number;
  ratePerKg: number;
  parcelSize: 'SMALL' | 'MEDIUM' | 'LARGE';
  sizeMultiplier: number;
}

interface QuoteResponse {
  appliedTier: string;
  baseFee: number;
  weightCharge: number;
  sizeMultiplier: number;
  zoneSurcharge: number;
  discountAmount: number;
  expressSurcharge: number;
  totalFee: number;
}

const readApiError = (error: unknown, fallback: string) => {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as {
      detail?: string;
      message?: string;
      title?: string;
    } | undefined;
    return payload?.detail || payload?.message || payload?.title || fallback;
  }
  return error instanceof Error ? error.message : fallback;
};

export default function AdminPricingPage() {
  const [zones, setZones] = useState<DeliveryZone[]>([]);
  const [rules, setRules] = useState<PricingRule[]>([]);
  const [activeTab, setActiveTab] = useState<'SIMULATOR' | 'ZONES' | 'RULES'>('SIMULATOR');

  // Modals
  const [showZoneModal, setShowZoneModal] = useState(false);
  const [showRuleModal, setShowRuleModal] = useState(false);
  const [editingZoneId, setEditingZoneId] = useState<number | null>(null);
  const [editingRuleId, setEditingRuleId] = useState<number | null>(null);

  // New Zone Form
  const [newZoneCode, setNewZoneCode] = useState('');
  const [newZoneName, setNewZoneName] = useState('');
  const [newDistrict, setNewDistrict] = useState('');
  const [newBaseSurcharge, setNewBaseSurcharge] = useState('25.00');

  // New Rule Form
  const [ruleOriginId, setRuleOriginId] = useState<number>(0);
  const [ruleDestId, setRuleDestId] = useState<number>(0);
  const [ruleBaseFee, setRuleBaseFee] = useState('400.00');
  const [ruleRatePerKg, setRuleRatePerKg] = useState('100.00');
  const [ruleSize, setRuleSize] = useState<'SMALL' | 'MEDIUM' | 'LARGE'>('MEDIUM');
  const [ruleMultiplier, setRuleMultiplier] = useState('1.25');

  // Simulator State
  const [calcOrigin, setCalcOrigin] = useState<number>(0);
  const [calcDest, setCalcDest] = useState<number>(0);
  const [calcWeight, setCalcWeight] = useState<number>(3.5);
  const [calcSize, setCalcSize] = useState<'SMALL' | 'MEDIUM' | 'LARGE'>('MEDIUM');
  const [calcTier, setCalcTier] = useState<'STANDARD' | 'MERCHANT' | 'EXPRESS'>('EXPRESS');
  const [quoteResult, setQuoteResult] = useState<QuoteResponse | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [pageError, setPageError] = useState('');
  const [pageNotice, setPageNotice] = useState('');

  const loadPricingData = useCallback(async () => {
    const [{ data: zData }, { data: rData }] = await Promise.all([
      api.get<DeliveryZone[]>('/api/v1/zones'),
      api.get<PricingRule[]>('/api/v1/pricing/rules'),
    ]);
    const nextZones = Array.isArray(zData) ? zData : [];
    const nextRules = Array.isArray(rData) ? rData : [];
    const firstZoneId = nextZones[0]?.id ?? 0;
    const secondZoneId = nextZones[1]?.id ?? firstZoneId;

    setZones(nextZones);
    setRules(nextRules);
    setCalcOrigin((current) => nextZones.some((zone) => zone.id === current) ? current : firstZoneId);
    setCalcDest((current) => nextZones.some((zone) => zone.id === current) ? current : secondZoneId);
    setRuleOriginId((current) => nextZones.some((zone) => zone.id === current) ? current : firstZoneId);
    setRuleDestId((current) => nextZones.some((zone) => zone.id === current) ? current : secondZoneId);

    if (nextZones.length < 2) {
      setQuoteResult(null);
      setPageError('');
    }
  }, []);

  // Load persisted pricing data before enabling the simulator.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- this effect synchronizes remote pricing data into the page state.
    loadPricingData().catch((error) => {
      setZones([]);
      setRules([]);
      setQuoteResult(null);
      setPageError(readApiError(error, 'Unable to load pricing data.'));
    });
  }, [loadPricingData]);

  const handleRefreshPricingData = async () => {
    setIsRefreshing(true);
    setPageError('');
    setPageNotice('');
    try {
      await loadPricingData();
      setPageNotice('Pricing data refreshed successfully.');
    } catch (error) {
      setPageError(readApiError(error, 'Unable to refresh pricing data.'));
    } finally {
      setIsRefreshing(false);
    }
  };

  const runQuoteCalculation = useCallback(async (signal?: AbortSignal) => {
    if (zones.length < 2) {
      setQuoteResult(null);
      setPageError('');
      return;
    }

    const origin = zones.find(z => z.id === calcOrigin) || zones[0];
    const dest = zones.find(z => z.id === calcDest) || zones[1] || zones[0];
    if (!origin || !dest) return;

    if (origin.id === dest.id) {
      setQuoteResult(null);
      setPageError('Origin and destination zones must be different.');
      return;
    }

    try {
      const response = await api.post<QuoteResponse>('/api/v1/parcels/quote', {
          originZoneId: origin.id,
          destinationZoneId: dest.id,
          weightKg: calcWeight,
          parcelSize: calcSize,
          tier: calcTier,
        }, {
        signal,
      });

      setQuoteResult(response.data);
      setPageError('');
    } catch (error) {
      if (axios.isCancel(error)) return;
      setQuoteResult(null);
      setPageError(readApiError(error, 'Unable to calculate a live quote.'));
    }
  }, [calcDest, calcOrigin, calcSize, calcTier, calcWeight, zones]);

  // Recalculate through the real pricing engine whenever parameters change.
  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- this effect runs the remote quote calculation for current inputs.
    void runQuoteCalculation(controller.signal);
    return () => controller.abort();
  }, [runQuoteCalculation]);

  const handleToggleZone = async (id: number) => {
    const zone = zones.find((candidate) => candidate.id === id);
    if (!zone) return;

    setIsSaving(true);
    setPageError('');
    setPageNotice('');
    try {
      await api.put(`/api/v1/zones/${id}`, { isActive: !zone.isActive });
      await loadPricingData();
      setPageNotice(`Zone ${zone.zoneCode} is now ${!zone.isActive ? 'active' : 'inactive'}.`);
    } catch (error) {
      setPageError(readApiError(error, 'Unable to update the delivery zone.'));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSaveZone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newZoneCode.trim() || !newZoneName.trim() || !newDistrict.trim()) return;

    const baseSurcharge = Number(newBaseSurcharge);
    if (!Number.isFinite(baseSurcharge) || baseSurcharge < 0) {
      setPageError('Base surcharge must be a number greater than or equal to zero.');
      setPageNotice('');
      return;
    }

    setIsSaving(true);
    setPageError('');
    setPageNotice('');
    try {
      const isEditing = editingZoneId !== null;
      const payload = {
        zoneCode: newZoneCode.trim().toUpperCase(),
        zoneName: newZoneName.trim(),
        district: newDistrict.trim(),
        baseSurcharge,
        ...(isEditing ? {} : { isActive: true }),
      };
      if (isEditing) {
        await api.put(`/api/v1/zones/${editingZoneId}`, payload);
      } else {
        await api.post('/api/v1/zones', payload);
      }
      await loadPricingData();
      setNewZoneCode('');
      setNewZoneName('');
      setNewDistrict('');
      setEditingZoneId(null);
      setShowZoneModal(false);
      setPageNotice(isEditing ? 'Delivery zone updated successfully.' : 'Delivery zone created successfully.');
    } catch (error) {
      setPageError(readApiError(error, 'Unable to save the delivery zone.'));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSaveRule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!zones.some((zone) => zone.id === ruleOriginId) || !zones.some((zone) => zone.id === ruleDestId)) return;

    if (ruleOriginId === ruleDestId) {
      setPageError('Origin and destination zones must be different.');
      setPageNotice('');
      return;
    }

    const baseFee = Number(ruleBaseFee);
    const ratePerKg = Number(ruleRatePerKg);
    const sizeMultiplier = Number(ruleMultiplier);
    if (!Number.isFinite(baseFee) || baseFee <= 0) {
      setPageError('Base fee must be greater than zero.');
      setPageNotice('');
      return;
    }
    if (!Number.isFinite(ratePerKg) || ratePerKg <= 0) {
      setPageError('Rate per kilogram must be greater than zero.');
      setPageNotice('');
      return;
    }
    if (!Number.isFinite(sizeMultiplier) || sizeMultiplier < 1) {
      setPageError('Size multiplier must be at least 1.00.');
      setPageNotice('');
      return;
    }

    setIsSaving(true);
    setPageError('');
    setPageNotice('');
    try {
      const isEditing = editingRuleId !== null;
      const payload = {
        baseFee,
        ratePerKg,
        sizeMultiplier,
        ...(isEditing ? {} : {
          originZoneId: ruleOriginId,
          destinationZoneId: ruleDestId,
          parcelSize: ruleSize,
        }),
      };
      if (isEditing) {
        await api.put(`/api/v1/pricing/rules/${editingRuleId}`, payload);
      } else {
        await api.post('/api/v1/pricing/rules', payload);
      }
      await loadPricingData();
      setEditingRuleId(null);
      setShowRuleModal(false);
      setPageNotice(isEditing ? 'Pricing rule updated successfully.' : 'Pricing rule created successfully.');
    } catch (error) {
      setPageError(readApiError(error, 'Unable to save the pricing rule.'));
    } finally {
      setIsSaving(false);
    }
  };

  const startEditingZone = (zone: DeliveryZone) => {
    setEditingZoneId(zone.id);
    setNewZoneCode(zone.zoneCode);
    setNewZoneName(zone.zoneName);
    setNewDistrict(zone.district);
    setNewBaseSurcharge(zone.baseSurcharge.toFixed(2));
    setShowZoneModal(true);
  };

  const startEditingRule = (rule: PricingRule) => {
    setEditingRuleId(rule.id);
    setRuleOriginId(rule.originZone.id);
    setRuleDestId(rule.destinationZone.id);
    setRuleBaseFee(rule.baseFee.toFixed(2));
    setRuleRatePerKg(rule.ratePerKg.toFixed(2));
    setRuleSize(rule.parcelSize);
    setRuleMultiplier(rule.sizeMultiplier.toFixed(2));
    setShowRuleModal(true);
  };

  const closeZoneModal = () => {
    setEditingZoneId(null);
    setShowZoneModal(false);
  };

  const closeRuleModal = () => {
    setEditingRuleId(null);
    setShowRuleModal(false);
  };

  const openNewZoneModal = () => {
    setEditingZoneId(null);
    setNewZoneCode('');
    setNewZoneName('');
    setNewDistrict('');
    setNewBaseSurcharge('25.00');
    setShowZoneModal(true);
  };

  const openNewRuleModal = () => {
    setEditingRuleId(null);
    setRuleBaseFee('400.00');
    setRuleRatePerKg('100.00');
    setRuleSize('MEDIUM');
    setRuleMultiplier('1.25');
    setShowRuleModal(true);
  };

  return (
    <AppShell>
      <div className="space-y-6">

        {(pageError || pageNotice) && (
          <div className={`rounded-xl border px-4 py-3 text-sm ${pageError
            ? 'border-red-200 bg-red-50 text-red-700'
            : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>
            <div className="flex items-center gap-2">
              {pageError ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
              <span>{pageError || pageNotice}</span>
            </div>
          </div>
        )}

        {/* Page Top Header */}
        <div className="pb-4 border-b border-slate-200">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
                Delivery Zones &amp; Dynamic Pricing Matrix
              </h1>
              <p className="text-sm text-slate-500 mt-1">
                Manage island-wide logistics zones, inter-zone tariffs, and live quotation calculation.
              </p>
            </div>

            <div className="flex items-center gap-2.5">
              <button
                onClick={() => void handleRefreshPricingData()}
                disabled={isRefreshing || isSaving}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 shadow-xs transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-60"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
                <span>{isRefreshing ? 'Refreshing...' : 'Refresh data'}</span>
              </button>
              <button
                onClick={openNewZoneModal}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 shadow-xs transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Zone</span>
              </button>
              <button
                onClick={openNewRuleModal}
                disabled={zones.length < 2}
                title={zones.length < 2 ? 'Create at least two delivery zones first.' : undefined}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Pricing Rule</span>
              </button>
            </div>
          </div>
        </div>

        {/* Top KPI Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
              <MapPin className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Zones</div>
              <div className="text-xl font-bold text-slate-900">{zones.filter(z => z.isActive).length} / {zones.length} Active</div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Pricing Rules</div>
              <div className="text-xl font-bold text-slate-900">{rules.length} Matrix Rules</div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Strategy Engine</div>
              <div className="text-xl font-bold text-slate-900">3 GoF Tiers</div>
            </div>
          </div>

          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-lg bg-slate-100 flex items-center justify-center text-slate-600 shrink-0">
              <CheckCircle2 className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Suite Health</div>
              <div className="text-xl font-bold text-slate-900">Automated</div>
            </div>
          </div>
        </div>

        {/* Tab Navigation Switcher */}
        <div className="flex bg-slate-100 p-1 rounded-xl max-w-xl">
          <button
            onClick={() => setActiveTab('SIMULATOR')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium transition-all cursor-pointer ${
              activeTab === 'SIMULATOR'
                ? 'bg-white text-slate-900 shadow-xs font-semibold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Zap className="w-4 h-4 text-slate-500" />
            <span>Dynamic Strategy Simulator</span>
          </button>

          <button
            onClick={() => setActiveTab('ZONES')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium transition-all cursor-pointer ${
              activeTab === 'ZONES'
                ? 'bg-white text-slate-900 shadow-xs font-semibold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <MapPin className="w-4 h-4 text-slate-500" />
            <span>Delivery Zones ({zones.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('RULES')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium transition-all cursor-pointer ${
              activeTab === 'RULES'
                ? 'bg-white text-slate-900 shadow-xs font-semibold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Layers className="w-4 h-4 text-slate-500" />
            <span>Pricing Rules ({rules.length})</span>
          </button>
        </div>

        {/* Tab 1: Live Strategy Simulator (Act 2A Demo) */}
        {activeTab === 'SIMULATOR' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">

            {zones.length < 2 && (
              <div role="status" className="lg:col-span-12 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                Add at least two delivery zones before creating pricing rules or calculating a quote.
              </div>
            )}

            {/* Left Controls Card */}
            <div className="lg:col-span-6 bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                    <Sliders className="w-4 h-4" />
                  </div>
                  <h2 className="text-base font-bold text-slate-900">Simulator Parameters</h2>
                </div>
                <span className="text-[11px] font-medium bg-slate-100 text-slate-600 px-2.5 py-0.5 rounded-full border border-slate-200">
                  Real-Time Dynamic Run
                </span>
              </div>

              {/* Origin & Destination */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Origin Zone
                  </label>
                  <select
                    value={calcOrigin}
                    onChange={(e) => setCalcOrigin(Number(e.target.value))}
                    className="w-full bg-white border border-slate-200 rounded-lg px-3 py-2 text-xs font-medium text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
                  >
                    {zones.map(z => (
                      <option key={z.id} value={z.id}>
                        {z.zoneCode} — {z.zoneName} ({z.district})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Destination Zone
                  </label>
                  <select
                    value={calcDest}
                    onChange={(e) => setCalcDest(Number(e.target.value))}
                    className="w-full bg-white border border-slate-200 rounded-lg px-3 py-2 text-xs font-medium text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900"
                  >
                    {zones.map(z => (
                      <option key={z.id} value={z.id}>
                        {z.zoneCode} — {z.zoneName} ({z.district})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Weight Slider */}
              <div className="bg-slate-50/70 p-3.5 rounded-xl border border-slate-200">
                <div className="flex justify-between items-center mb-2">
                  <label className="text-xs font-semibold text-slate-600 uppercase tracking-wider flex items-center gap-1.5">
                    <Scale className="w-3.5 h-3.5 text-slate-500" />
                    Package Weight
                  </label>
                  <span className="text-xs font-bold text-slate-900 bg-white px-2.5 py-0.5 rounded-md border border-slate-200 font-mono">
                    {calcWeight.toFixed(2)} kg
                  </span>
                </div>
                <input
                  type="range"
                  min="0.2"
                  max="30"
                  step="0.1"
                  value={calcWeight}
                  onChange={(e) => setCalcWeight(parseFloat(e.target.value))}
                  className="w-full accent-slate-900 cursor-pointer h-2 bg-slate-200 rounded-lg"
                />
                <div className="flex justify-between text-[11px] text-slate-400 mt-1 font-mono">
                  <span>0.2 kg (Docs)</span>
                  <span>15.0 kg</span>
                  <span>30.0 kg (Freight)</span>
                </div>
              </div>

              {/* Package Dimensions */}
              <div>
                <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <Box className="w-3.5 h-3.5 text-slate-500" />
                  Dimension Tier (Multiplier)
                </label>
                <div className="grid grid-cols-3 gap-2.5">
                  {(['SMALL', 'MEDIUM', 'LARGE'] as const).map(size => {
                    const mult = size === 'SMALL' ? '1.0×' : (size === 'MEDIUM' ? '1.25×' : '1.5×');
                    const isSelected = calcSize === size;
                    return (
                      <button
                        key={size}
                        type="button"
                        onClick={() => setCalcSize(size)}
                        className={`p-3 rounded-xl border text-center transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-slate-900 border-slate-900 shadow-xs'
                            : 'bg-white border-slate-200 hover:border-slate-300'
                        }`}
                      >
                        <div className={`text-xs font-bold ${isSelected ? 'text-white' : 'text-slate-800'}`}>
                          {size}
                        </div>
                        <div className={`text-[11px] mt-0.5 font-medium ${isSelected ? 'text-slate-300' : 'text-slate-500'}`}>
                          {mult} Multiplier
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Pricing Strategy Tier */}
              <div>
                <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <Zap className="w-3.5 h-3.5 text-slate-500" />
                  GoF Pricing Strategy Tier
                </label>
                <div className="grid grid-cols-3 gap-2.5">
                  <button
                    type="button"
                    onClick={() => setCalcTier('STANDARD')}
                    className={`p-3 rounded-xl border text-center transition-all cursor-pointer ${
                      calcTier === 'STANDARD'
                        ? 'bg-slate-900 border-slate-900 shadow-xs'
                        : 'bg-white border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <div className={`text-xs font-bold ${calcTier === 'STANDARD' ? 'text-white' : 'text-slate-800'}`}>STANDARD</div>
                    <div className={`text-[11px] mt-0.5 font-medium ${calcTier === 'STANDARD' ? 'text-slate-300' : 'text-slate-500'}`}>Regular Base</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCalcTier('MERCHANT')}
                    className={`p-3 rounded-xl border text-center transition-all cursor-pointer ${
                      calcTier === 'MERCHANT'
                        ? 'bg-slate-900 border-slate-900 shadow-xs'
                        : 'bg-white border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <div className={`text-xs font-bold ${calcTier === 'MERCHANT' ? 'text-white' : 'text-slate-800'}`}>MERCHANT</div>
                    <div className={`text-[11px] mt-0.5 font-medium ${calcTier === 'MERCHANT' ? 'text-slate-300' : 'text-slate-500'}`}>15% Discount</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCalcTier('EXPRESS')}
                    className={`p-3 rounded-xl border text-center transition-all cursor-pointer ${
                      calcTier === 'EXPRESS'
                        ? 'bg-slate-900 border-slate-900 shadow-xs'
                        : 'bg-white border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <div className={`text-xs font-bold ${calcTier === 'EXPRESS' ? 'text-white' : 'text-slate-800'}`}>EXPRESS</div>
                    <div className={`text-[11px] mt-0.5 font-medium ${calcTier === 'EXPRESS' ? 'text-slate-300' : 'text-slate-500'}`}>1.5× Expedited</div>
                  </button>
                </div>
              </div>

            </div>

            {/* Right Receipt & Breakdown Card */}
            <div className="lg:col-span-6 flex flex-col justify-between bg-white text-slate-900 rounded-xl p-5 shadow-xs border border-slate-200">
              
              <div>
                {/* Header */}
                <div className="flex items-center justify-between pb-3.5 border-b border-slate-100 mb-4">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                      <Calculator className="w-4 h-4 text-slate-600" />
                      Dynamic Quotation Output
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5">
                      Orchestrated by Spring GoF Strategy Engine
                    </p>
                  </div>
                  <span className="px-2.5 py-1 rounded-md text-xs font-semibold tracking-wide uppercase bg-slate-100 text-slate-700 border border-slate-200">
                    {calcTier} STRATEGY
                  </span>
                </div>

                {/* Mathematical Formulation Display */}
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 mb-4 font-mono text-xs">
                  <span className="text-[10px] text-slate-400 uppercase font-semibold tracking-wider block mb-1">
                    Applied Strategy Formula:
                  </span>
                  <div className="text-slate-800 font-semibold">
                    {calcTier === 'MERCHANT' && (
                      <span>Total = [ (BaseFee×0.85 + Wt×Rate×0.85) × Multiplier ] + Surcharges</span>
                    )}
                    {calcTier === 'EXPRESS' && (
                      <span>Total = [ (BaseFee + Wt×Rate) × Multiplier × 1.5 ] + Surcharges</span>
                    )}
                    {calcTier === 'STANDARD' && (
                      <span>Total = [ (BaseFee + Wt×Rate) × Multiplier ] + Surcharges</span>
                    )}
                  </div>
                </div>

                {/* Line Items Breakdown */}
                {quoteResult && (
                  <div className="space-y-2.5 text-xs sm:text-sm">
                    <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                      <span className="text-slate-600">Inter-Zone Base Tariff:</span>
                      <span className="font-mono font-bold text-slate-900">LKR {quoteResult.baseFee.toFixed(2)}</span>
                    </div>

                    <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                      <div>
                        <span className="text-slate-600">Weight Charge:</span>
                        <span className="text-xs text-slate-400 font-mono ml-1.5">({calcWeight.toFixed(2)} kg)</span>
                      </div>
                      <span className="font-mono font-bold text-slate-900">LKR {quoteResult.weightCharge.toFixed(2)}</span>
                    </div>

                    <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                      <div>
                        <span className="text-slate-600">Size Multiplier Effect:</span>
                        <span className="text-xs text-slate-400 font-mono ml-1.5">({calcSize} = {quoteResult.sizeMultiplier.toFixed(2)}×)</span>
                      </div>
                      <span className="font-mono font-medium text-slate-700 bg-slate-100 px-2 py-0.5 rounded text-xs border border-slate-200">
                        Active
                      </span>
                    </div>

                    <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                      <span className="text-slate-600">Provincial Surcharges:</span>
                      <span className="font-mono font-bold text-slate-900">LKR {quoteResult.zoneSurcharge.toFixed(2)}</span>
                    </div>

                    {calcTier === 'MERCHANT' && (
                      <div className="flex justify-between items-center py-2 px-3 rounded-lg bg-emerald-50/70 border border-emerald-200/80 text-emerald-800">
                        <span className="flex items-center gap-1.5 font-semibold text-xs">
                          <Percent className="w-3.5 h-3.5 text-emerald-600" />
                          Corporate Merchant Discount (15%):
                        </span>
                        <span className="font-mono font-bold text-emerald-700">- LKR {quoteResult.discountAmount.toFixed(2)}</span>
                      </div>
                    )}

                    {calcTier === 'EXPRESS' && (
                      <div className="flex justify-between items-center py-2 px-3 rounded-lg bg-amber-50/70 border border-amber-200/80 text-amber-800">
                        <span className="flex items-center gap-1.5 font-semibold text-xs">
                          <Zap className="w-3.5 h-3.5 text-amber-600" />
                          Express Expedited Surcharge (50%):
                        </span>
                        <span className="font-mono font-bold text-amber-700">+ LKR {quoteResult.expressSurcharge.toFixed(2)}</span>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Total Card */}
              {quoteResult && (
                <div className="mt-5 p-4 rounded-xl bg-slate-900 text-white flex items-center justify-between shadow-xs">
                  <div>
                    <span className="text-xs uppercase tracking-wider font-semibold text-slate-300 block">
                      Grand Total Fee (LKR)
                    </span>
                    <span className="text-[11px] text-slate-400">
                      Live PricingEngine quote
                    </span>
                  </div>
                  <div className="text-2xl sm:text-3xl font-extrabold text-white font-mono tracking-tight">
                    LKR {quoteResult.totalFee.toFixed(2)}
                  </div>
                </div>
              )}

            </div>

          </div>
        )}

        {/* Tab 2: Delivery Zones */}
        {activeTab === 'ZONES' && (
          <div className="bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden">
            <div className="p-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">Configured Sri Lankan Delivery Zones</h3>
                <p className="text-xs text-slate-500">Maps directly to the PostgreSQL delivery_zones table</p>
              </div>
              <button
                onClick={openNewZoneModal}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Zone</span>
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-slate-50 text-slate-500 uppercase text-[11px] tracking-wider border-b border-slate-200 font-semibold">
                  <tr>
                    <th className="px-5 py-3">Zone Code</th>
                    <th className="px-5 py-3">Zone Name</th>
                    <th className="px-5 py-3">District</th>
                    <th className="px-5 py-3">Base Surcharge</th>
                    <th className="px-5 py-3">Status</th>
                    <th className="px-5 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {zones.map(z => (
                    <tr key={z.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3 font-mono font-semibold text-slate-900">{z.zoneCode}</td>
                      <td className="px-5 py-3 font-medium text-slate-900">{z.zoneName}</td>
                      <td className="px-5 py-3 text-slate-600">{z.district}</td>
                      <td className="px-5 py-3 font-mono text-slate-800">LKR {z.baseSurcharge.toFixed(2)}</td>
                      <td className="px-5 py-3">
                        {z.isActive ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/80">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-600 border border-slate-200">
                            <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                            Inactive
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <button
                          onClick={() => startEditingZone(z)}
                          disabled={isSaving}
                          className="px-3 py-1 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => handleToggleZone(z.id)}
                          disabled={isSaving}
                          aria-label={`${z.isActive ? 'Deactivate' : 'Activate'} ${z.zoneCode}`}
                          className="px-3 py-1 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                        >
                          {z.isActive ? 'Deactivate' : 'Activate'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 3: Pricing Rules Matrix */}
        {activeTab === 'RULES' && (
          <div className="bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden">
            <div className="p-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">Inter-Zone Pricing Rules Matrix</h3>
                <p className="text-xs text-slate-500">Unique constraint across (origin_zone_id, destination_zone_id, parcel_size)</p>
              </div>
              <button
                onClick={openNewRuleModal}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Rule</span>
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-slate-50 text-slate-500 uppercase text-[11px] tracking-wider border-b border-slate-200 font-semibold">
                  <tr>
                    <th className="px-5 py-3">Route (Origin → Destination)</th>
                    <th className="px-5 py-3">Parcel Size</th>
                    <th className="px-5 py-3">Base Fee</th>
                    <th className="px-5 py-3">Rate / Kg</th>
                    <th className="px-5 py-3">Multiplier</th>
                    <th className="px-5 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rules.map(r => (
                    <tr key={r.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3 font-semibold text-slate-900 flex items-center gap-2">
                        <span className="font-mono text-slate-900">{r.originZone.zoneCode}</span>
                        <ArrowRight className="w-3.5 h-3.5 text-slate-400" />
                        <span className="font-mono text-slate-900">{r.destinationZone.zoneCode}</span>
                      </td>
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium bg-slate-100 text-slate-700 border border-slate-200">
                          {r.parcelSize}
                        </span>
                      </td>
                      <td className="px-5 py-3 font-mono font-semibold text-slate-800">LKR {r.baseFee.toFixed(2)}</td>
                      <td className="px-5 py-3 font-mono font-semibold text-slate-800">LKR {r.ratePerKg.toFixed(2)}</td>
                      <td className="px-5 py-3 font-mono text-slate-900 font-bold">{r.sizeMultiplier.toFixed(2)}×</td>
                      <td className="px-5 py-3 text-right">
                        <button
                          onClick={() => startEditingRule(r)}
                          disabled={isSaving}
                          className="px-3 py-1 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                        >
                          Edit
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Modal: Add Delivery Zone */}
        {showZoneModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
            <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4">
              <div className="flex justify-between items-center border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-slate-900">{editingZoneId === null ? 'Add' : 'Edit'} Sri Lankan Delivery Zone</h3>
                <button onClick={closeZoneModal} className="text-slate-400 hover:text-slate-600">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleSaveZone} className="space-y-3.5">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Zone Code (e.g. NP-JAF-01)
                  </label>
                  <input
                    type="text"
                    value={newZoneCode}
                    onChange={(e) => setNewZoneCode(e.target.value)}
                    placeholder="e.g. NP-JAF-01"
                    required
                    disabled={editingZoneId !== null}
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Zone Name
                  </label>
                  <input
                    type="text"
                    value={newZoneName}
                    onChange={(e) => setNewZoneName(e.target.value)}
                    placeholder="e.g. Jaffna Metro Central"
                    required
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    District
                  </label>
                  <input
                    type="text"
                    value={newDistrict}
                    onChange={(e) => setNewDistrict(e.target.value)}
                    placeholder="e.g. Jaffna"
                    required
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Base Surcharge (LKR)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={newBaseSurcharge}
                    onChange={(e) => setNewBaseSurcharge(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={closeZoneModal}
                    className="px-4 py-2 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                  >
                    {isSaving ? 'Saving...' : editingZoneId === null ? 'Save Zone' : 'Update Zone'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Modal: Add Pricing Rule */}
        {showRuleModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
            <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4">
              <div className="flex justify-between items-center border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-slate-900">{editingRuleId === null ? 'Add' : 'Edit'} Pricing Matrix Rule</h3>
                <button onClick={closeRuleModal} className="text-slate-400 hover:text-slate-600">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleSaveRule} className="space-y-3.5">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Origin
                    </label>
                    <select
                      value={ruleOriginId}
                      onChange={(e) => setRuleOriginId(Number(e.target.value))}
                      disabled={editingRuleId !== null}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800"
                    >
                      {zones.map(z => (
                        <option key={z.id} value={z.id}>{z.zoneCode}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Destination
                    </label>
                    <select
                      value={ruleDestId}
                      onChange={(e) => setRuleDestId(Number(e.target.value))}
                      disabled={editingRuleId !== null}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800"
                    >
                      {zones.map(z => (
                        <option key={z.id} value={z.id}>{z.zoneCode}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Base Fee (LKR)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      required
                      value={ruleBaseFee}
                      onChange={(e) => setRuleBaseFee(e.target.value)}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-900"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Rate / Kg (LKR)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      required
                      value={ruleRatePerKg}
                      onChange={(e) => setRuleRatePerKg(e.target.value)}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-900"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Size Tier
                    </label>
                    <select
                      value={ruleSize}
                      onChange={(e) => setRuleSize(e.target.value as 'SMALL' | 'MEDIUM' | 'LARGE')}
                      disabled={editingRuleId !== null}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800"
                    >
                      <option value="SMALL">SMALL (1.0x)</option>
                      <option value="MEDIUM">MEDIUM (1.25x)</option>
                      <option value="LARGE">LARGE (1.5x)</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Multiplier
                    </label>
                    <input
                      type="number"
                      step="0.05"
                      min="1"
                      required
                      value={ruleMultiplier}
                      onChange={(e) => setRuleMultiplier(e.target.value)}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-900"
                    />
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={closeRuleModal}
                    className="px-4 py-2 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                  >
                    {isSaving ? 'Saving...' : editingRuleId === null ? 'Save Rule' : 'Update Rule'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </AppShell>
  );
}
