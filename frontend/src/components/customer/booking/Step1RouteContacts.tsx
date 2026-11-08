'use client';

import React, { useMemo } from 'react';
import {
  MapPin,
  User,
  Building,
  ArrowRight,
  ShieldCheck,
} from 'lucide-react';
import { BookingFormData, DeliveryZone } from '@/types/booking';
import { SRI_LANKAN_PHONE_REGEX } from '@/constants/booking';
import { Button, Input, PhoneInput, Select } from '@/components/ui';

interface Step1RouteContactsProps {
  formData: BookingFormData;
  onChange: (updates: Partial<BookingFormData>) => void;
  zones: DeliveryZone[];
  loadingZones: boolean;
  onNext: () => void;
  isLoggedIn: boolean;
}

export default function Step1RouteContacts({
  formData,
  onChange,
  zones,
  loadingZones,
  onNext,
  isLoggedIn,
}: Step1RouteContactsProps) {
  // Validate phone numbers in real time
  const isSenderPhoneValid = useMemo(
    () => SRI_LANKAN_PHONE_REGEX.test(formData.senderPhone.trim()),
    [formData.senderPhone]
  );

  const isRecipientPhoneValid = useMemo(
    () => SRI_LANKAN_PHONE_REGEX.test(formData.recipientPhone.trim()),
    [formData.recipientPhone]
  );

  // Form completeness check
  const isFormValid = useMemo(() => {
    return (
      formData.senderName.trim().length >= 2 &&
      isSenderPhoneValid &&
      formData.senderAddress.trim().length >= 2 &&
      Boolean(formData.originZoneId) &&
      formData.recipientName.trim().length >= 2 &&
      isRecipientPhoneValid &&
      formData.recipientAddress.trim().length >= 2 &&
      Boolean(formData.destinationZoneId)
    );
  }, [formData, isSenderPhoneValid, isRecipientPhoneValid]);

  const zoneOptions = useMemo(
    () =>
      zones.map((z) => ({
        value: z.id,
        label: `${z.zoneName} (${z.district} District)`,
      })),
    [zones]
  );

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* ================================================================= */}
        {/* SENDER CARD                                                      */}
        {/* ================================================================= */}
        <section
          aria-labelledby="sender-card-title"
          className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-2xs space-y-4"
        >
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-xs">
                1A
              </div>
              <div>
                <h2 id="sender-card-title" className="text-sm font-bold text-slate-900">
                  Sender (Pickup Point)
                </h2>
                <p className="text-xs text-slate-500">Origin contact details for collection</p>
              </div>
            </div>
            {isLoggedIn && (
              <span className="text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 rounded-full flex items-center gap-1">
                <ShieldCheck className="w-3 h-3" />
                Verified Customer
              </span>
            )}
          </div>

          <div className="space-y-3.5">
            {/* Sender Name */}
            <Input
              id="senderName"
              label="Sender Full Name"
              required
              value={formData.senderName}
              onChange={(e) => onChange({ senderName: e.target.value })}
              placeholder="e.g. Saman Kumara"
              leftIcon={<User className="w-4 h-4" />}
            />

            {/* Sender Phone */}
            <PhoneInput
              id="senderPhone"
              label="Phone Number (Sri Lanka)"
              required
              value={formData.senderPhone}
              onChange={(val) => onChange({ senderPhone: val })}
              placeholder="07XXXXXXXX"
              helperText="Must be 10 digits starting with 07."
            />

            {/* Origin Zone */}
            <Select
              id="originZoneId"
              label="Origin Logistics Zone"
              required
              value={formData.originZoneId}
              onChange={(e) => onChange({ originZoneId: e.target.value })}
              options={zoneOptions}
              loading={loadingZones}
              leftIcon={<Building className="w-4 h-4" />}
            />

            {/* Pickup Address */}
            <Input
              id="senderAddress"
              label="Pickup Street Address"
              required
              value={formData.senderAddress}
              onChange={(e) => onChange({ senderAddress: e.target.value })}
              placeholder="e.g. No. 45, Galle Road, Colombo 03"
              leftIcon={<MapPin className="w-4 h-4" />}
            />
          </div>
        </section>

        {/* ================================================================= */}
        {/* RECIPIENT CARD                                                   */}
        {/* ================================================================= */}
        <section
          aria-labelledby="recipient-card-title"
          className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-2xs space-y-4"
        >
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-xs">
                1B
              </div>
              <div>
                <h2 id="recipient-card-title" className="text-sm font-bold text-slate-900">
                  Recipient (Drop-off Destination)
                </h2>
                <p className="text-xs text-slate-500">Receiver contact and delivery address</p>
              </div>
            </div>
          </div>

          <div className="space-y-3.5">
            {/* Recipient Name */}
            <Input
              id="recipientName"
              label="Recipient Full Name"
              required
              value={formData.recipientName}
              onChange={(e) => onChange({ recipientName: e.target.value })}
              placeholder="e.g. Nadeesha Perera"
              leftIcon={<User className="w-4 h-4" />}
            />

            {/* Recipient Phone */}
            <PhoneInput
              id="recipientPhone"
              label="Phone Number (Sri Lanka)"
              required
              value={formData.recipientPhone}
              onChange={(val) => onChange({ recipientPhone: val })}
              placeholder="07XXXXXXXX"
              helperText="Courier will call this number prior to doorstep delivery."
            />

            {/* Destination Zone */}
            <Select
              id="destinationZoneId"
              label="Destination Logistics Zone"
              required
              value={formData.destinationZoneId}
              onChange={(e) => onChange({ destinationZoneId: e.target.value })}
              options={zoneOptions}
              loading={loadingZones}
              leftIcon={<Building className="w-4 h-4" />}
            />

            {/* Delivery Address */}
            <Input
              id="recipientAddress"
              label="Delivery Street Address"
              required
              value={formData.recipientAddress}
              onChange={(e) => onChange({ recipientAddress: e.target.value })}
              placeholder="e.g. 128/B, Kandy Road, Kadawatha"
              leftIcon={<MapPin className="w-4 h-4" />}
            />
          </div>
        </section>
      </div>

      {/* Navigation Controls */}
      <div className="flex justify-end pt-2">
        <Button
          type="button"
          onClick={onNext}
          disabled={!isFormValid}
          rightIcon={<ArrowRight className="w-4 h-4" />}
        >
          Continue to Parcel Specs
        </Button>
      </div>
    </div>
  );
}
