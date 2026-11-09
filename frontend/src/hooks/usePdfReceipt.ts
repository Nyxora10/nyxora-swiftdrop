'use client';

import { useState, useCallback } from 'react';
import { authStorage } from '@/lib/auth';
import { BookingResponse } from '@/types/booking';
import { downloadReceiptPdf, generateDemoPdf } from '@/services/parcelService';

export interface UsePdfReceiptOptions {
  toast: {
    success: (msg: string) => void;
    error: (msg: string) => void;
  };
}

export function usePdfReceipt({ toast }: UsePdfReceiptOptions) {
  const [downloadingPdf, setDownloadingPdf] = useState<boolean>(false);

  const downloadPdf = useCallback(
    async (bookingResult: BookingResponse | null) => {
      if (!bookingResult?.parcelId) return;
      setDownloadingPdf(true);

      const token = authStorage.getAccessToken();
      const isMock =
        !token ||
        token.startsWith('mock-') ||
        bookingResult.parcelId.startsWith('00000000');

      if (isMock) {
        setTimeout(() => {
          try {
            const blob = generateDemoPdf(bookingResult);
            triggerBlobDownload(blob, `SwiftDrop-Receipt-${bookingResult.trackingNumber}.pdf`);
            toast.success('Digital PDF delivery receipt downloaded successfully!');
          } catch (e) {
            toast.error('Failed to generate mock PDF.');
          } finally {
            setDownloadingPdf(false);
          }
        }, 500);
        return;
      }

      try {
        const blob = await downloadReceiptPdf(bookingResult.parcelId);
        triggerBlobDownload(blob, `SwiftDrop-Receipt-${bookingResult.trackingNumber}.pdf`);
        toast.success('Digital PDF delivery receipt downloaded successfully!');
      } catch (err: any) {
        console.error('Failed to download PDF receipt:', err);
        toast.error('Unable to generate PDF receipt. Please check authorization or try again later.');
      } finally {
        setDownloadingPdf(false);
      }
    },
    [toast]
  );

  return { downloadingPdf, downloadPdf };
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const downloadUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(downloadUrl);
}

