'use client';

import { useState, useEffect, useCallback } from 'react';
import { BookingFormData, DeliveryZone } from '@/types/booking';
import { INITIAL_FORM_DATA } from '@/constants/booking';

export interface UseBookingWizardOptions {
  userFullName?: string | null;
  zones: DeliveryZone[];
}

export function useBookingWizard({ userFullName, zones }: UseBookingWizardOptions) {
  const [currentStep, setCurrentStep] = useState<number>(1);
  const [formData, setFormData] = useState<BookingFormData>(INITIAL_FORM_DATA);

  // Auto-prefill sender details from active authenticated session
  useEffect(() => {
    if (userFullName && !formData.senderName) {
      setFormData((prev) => ({ ...prev, senderName: userFullName }));
    }
  }, [userFullName]);

  // Synchronize initial zone selections once zones are resolved
  useEffect(() => {
    if (zones.length >= 2) {
      setFormData((prev) => {
        const hasValidOrigin = zones.some((z) => z.id === prev.originZoneId);
        const hasValidDest = zones.some((z) => z.id === prev.destinationZoneId);

        return {
          ...prev,
          originZoneId: hasValidOrigin ? prev.originZoneId : zones[0].id,
          destinationZoneId: hasValidDest ? prev.destinationZoneId : zones[1].id,
        };
      });
    }
  }, [zones]);

  const updateFormData = useCallback((updates: Partial<BookingFormData>) => {
    setFormData((prev) => ({ ...prev, ...updates }));
  }, []);

  const nextStep = useCallback(() => {
    setCurrentStep((prev) => Math.min(prev + 1, 4));
  }, []);

  const prevStep = useCallback(() => {
    setCurrentStep((prev) => Math.max(prev - 1, 1));
  }, []);

  const goToStep = useCallback((step: number) => {
    setCurrentStep(step);
  }, []);

  const resetForm = useCallback(() => {
    setFormData({
      ...INITIAL_FORM_DATA,
      senderName: userFullName || '',
      originZoneId: zones[0]?.id || '1',
      destinationZoneId: zones[1]?.id || '2',
    });
    setCurrentStep(1);
  }, [userFullName, zones]);

  return {
    currentStep,
    formData,
    updateFormData,
    nextStep,
    prevStep,
    goToStep,
    resetForm,
  };
}

