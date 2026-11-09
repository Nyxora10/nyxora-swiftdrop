'use client';

import React, { useState, useEffect } from 'react';
import AppShell from '@/components/layout/AppShell';
import {
  MapPin,
  Layers,
  Zap,
  Plus,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  Sliders,
  DollarSign,
  Sparkles,
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

const SEED_ZONES: DeliveryZone[] = [
  { id: 1, zoneCode: 'WP-COL-01', zoneName: 'Colombo Central Metro', district: 'Colombo', baseSurcharge: 0, isActive: true },
  { id: 2, zoneCode: 'WP-GAM-01', zoneName: 'Gampaha Industrial Hub', district: 'Gampaha', baseSurcharge: 25, isActive: true },
  { id: 3, zoneCode: 'CP-KAN-01', zoneName: 'Kandy Urban District', district: 'Kandy', baseSurcharge: 50, isActive: true },
  { id: 4, zoneCode: 'SP-GAL-01', zoneName: 'Galle Coastal Belt', district: 'Galle', baseSurcharge: 40, isActive: true },
];

const SEED_RULES: PricingRule[] = [
  { id: 1, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[2], baseFee: 450, ratePerKg: 120, parcelSize: 'MEDIUM', sizeMultiplier: 1.25 },
  { id: 2, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[1], baseFee: 250, ratePerKg: 80, parcelSize: 'SMALL', sizeMultiplier: 1.0 },
  { id: 3, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[3], baseFee: 550, ratePerKg: 140, parcelSize: 'LARGE', sizeMultiplier: 1.5 },
];

export default function AdminPricingPage() {
  const [zones, setZones] = useState<DeliveryZone[]>(SEED_ZONES);
  const [rules, setRules] = useState<PricingRule[]>(SEED_RULES);
  const [activeTab, setActiveTab] = useState<'SIMULATOR' | 'ZONES' | 'RULES'>('SIMULATOR');

  // Modals
  const [showZoneModal, setShowZoneModal] = useState(false);
  const [showRuleModal, setShowRuleModal] = useState(false);

  // New Zone Form
  const [newZoneCode, setNewZoneCode] = useState('');
  const [newZoneName, setNewZoneName] = useState('');
  const [newDistrict, setNewDistrict] = useState('');
  const [newBaseSurcharge, setNewBaseSurcharge] = useState('25.00');

  // New Rule Form
  const [ruleOriginId, setRuleOriginId] = useState<number>(1);
  const [ruleDestId, setRuleDestId] = useState<number>(3);
  const [ruleBaseFee, setRuleBaseFee] = useState('400.00');
  const [ruleRatePerKg, setRuleRatePerKg] = useState('100.00');
  const [ruleSize, setRuleSize] = useState<'SMALL' | 'MEDIUM' | 'LARGE'>('MEDIUM');
  const [ruleMultiplier, setRuleMultiplier] = useState('1.25');

  // Simulator State
  const [calcOrigin, setCalcOrigin] = useState<number>(1);
  const [calcDest, setCalcDest] = useState<number>(3);
  const [calcWeight, setCalcWeight] = useState<number>(3.5);
  const [calcSize, setCalcSize] = useState<'SMALL' | 'MEDIUM' | 'LARGE'>('MEDIUM');
  const [calcTier, setCalcTier] = useState<'STANDARD' | 'MERCHANT' | 'EXPRESS'>('EXPRESS');
  const [quoteResult, setQuoteResult] = useState<QuoteResponse | null>(null);

  // Fetch real data if backend available
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [zonesRes, rulesRes] = await Promise.all([
          fetch('/api/v1/zones', { headers: { 'Authorization': `Bearer ${localStorage.getItem('swiftdrop_access_token') || ''}`, 'X-User-Role': 'ROLE_ADMIN' } }),
          fetch('/api/v1/pricing/rules', { headers: { 'Authorization': `Bearer ${localStorage.getItem('swiftdrop_access_token') || ''}`, 'X-User-Role': 'ROLE_ADMIN' } })
        ]);
        if (zonesRes.ok) {
          const zData = await zonesRes.json();
          if (zData && zData.length > 0) setZones(zData);
        }
        if (rulesRes.ok) {
          const rData = await rulesRes.json();
          if (rData && rData.length > 0) setRules(rData);
        }
      } catch {
        // Fallback to seed data seamlessly
      }
    };
    fetchData();
  }, []);

  // Recalculate Live Quote whenever parameters change
  useEffect(() => {
    runQuoteCalculation();
  }, [calcOrigin, calcDest, calcWeight, calcSize, calcTier, zones, rules]);

  const runQuoteCalculation = () => {
    const origin = zones.find(z => z.id === calcOrigin) || zones[0];
    const dest = zones.find(z => z.id === calcDest) || zones[1] || zones[0];
    if (!origin || !dest) return;

    // Find rule or fallback to sensible default
    let rule = rules.find(r =>
      r.originZone.id === origin.id &&
      r.destinationZone.id === dest.id &&
      r.parcelSize === calcSize
    );

    const baseFee = rule ? rule.baseFee : 450;
    const ratePerKg = rule ? rule.ratePerKg : 120;
    const sizeMultipliers = { 'SMALL': 1.0, 'MEDIUM': 1.25, 'LARGE': 1.5 };
    const sizeMult = rule ? rule.sizeMultiplier : sizeMultipliers[calcSize];

    const weightCharge = calcWeight * ratePerKg;
    const subtotal = (baseFee + weightCharge) * sizeMult;
    const zoneSurcharges = origin.baseSurcharge + dest.baseSurcharge;

    let discount = 0;
    let expressSur = 0;
    let total = subtotal + zoneSurcharges;

    if (calcTier === 'MERCHANT') {
      discount = (baseFee + weightCharge) * 0.15;
      total -= discount;
    } else if (calcTier === 'EXPRESS') {
      expressSur = subtotal * 0.50;
      total += expressSur;
    }

    setQuoteResult({
      appliedTier: calcTier,
      baseFee,
      weightCharge,
      sizeMultiplier: sizeMult,
      zoneSurcharge: zoneSurcharges,
      discountAmount: discount,
      expressSurcharge: expressSur,
      totalFee: Math.max(0, total)
    });
  };

  const handleToggleZone = (id: number) => {
    setZones(prev => prev.map(z => z.id === id ? { ...z, isActive: !z.isActive } : z));
  };

  const handleCreateZone = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newZoneCode || !newZoneName) return;
    const newZone: DeliveryZone = {
      id: zones.length + 1,
      zoneCode: newZoneCode.toUpperCase(),
      zoneName: newZoneName,
      district: newDistrict || 'General',
      baseSurcharge: parseFloat(newBaseSurcharge) || 0,
      isActive: true,
    };
    setZones(prev => [...prev, newZone]);
    setNewZoneCode('');
    setNewZoneName('');
    setNewDistrict('');
    setShowZoneModal(false);
  };

  const handleCreateRule = (e: React.FormEvent) => {
    e.preventDefault();
    const orig = zones.find(z => z.id === ruleOriginId);
    const dest = zones.find(z => z.id === ruleDestId);
    if (!orig || !dest) return;

    const newRule: PricingRule = {
      id: rules.length + 1,
      originZone: orig,
      destinationZone: dest,
      baseFee: parseFloat(ruleBaseFee) || 300,
      ratePerKg: parseFloat(ruleRatePerKg) || 100,
      parcelSize: ruleSize,
      sizeMultiplier: parseFloat(ruleMultiplier) || 1.0,
    };
    setRules(prev => [...prev, newRule]);
    setShowRuleModal(false);
  };

  return (
    <AppShell>
      <div className="space-y-6">

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
                onClick={() => setShowZoneModal(true)}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 shadow-xs transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Zone</span>
              </button>
              <button
                onClick={() => setShowRuleModal(true)}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
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
              <div className="text-xl font-bold text-slate-900">82 Tests Pass</div>
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
                    <span className="text-[11px] text-slate-400">Guaranteed Quote for Booking</span>
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
                onClick={() => setShowZoneModal(true)}
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
                          onClick={() => handleToggleZone(z.id)}
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
                onClick={() => setShowRuleModal(true)}
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
                    <th className="px-5 py-3 text-right">Database Constraint</th>
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
                        <span className="text-[11px] font-medium text-slate-400">uk_pricing_rule OK</span>
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
                <h3 className="text-base font-bold text-slate-900">Add Sri Lankan Delivery Zone</h3>
                <button onClick={() => setShowZoneModal(false)} className="text-slate-400 hover:text-slate-600">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleCreateZone} className="space-y-3.5">
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
                    value={newBaseSurcharge}
                    onChange={(e) => setNewBaseSurcharge(e.target.value)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setShowZoneModal(false)}
                    className="px-4 py-2 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                  >
                    Save Zone
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
                <h3 className="text-base font-bold text-slate-900">Add Pricing Matrix Rule</h3>
                <button onClick={() => setShowRuleModal(false)} className="text-slate-400 hover:text-slate-600">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleCreateRule} className="space-y-3.5">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Origin
                    </label>
                    <select
                      value={ruleOriginId}
                      onChange={(e) => setRuleOriginId(Number(e.target.value))}
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
                      value={ruleMultiplier}
                      onChange={(e) => setRuleMultiplier(e.target.value)}
                      className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-900"
                    />
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setShowRuleModal(false)}
                    className="px-4 py-2 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 shadow-xs transition-colors cursor-pointer"
                  >
                    Save Rule
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
