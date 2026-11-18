'use client';

import React, { useState, useRef } from 'react';
import Link from 'next/link';
import {
  UploadCloud,
  FileSpreadsheet,
  FileCode,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  XCircle,
  Download,
  RefreshCw,
  ArrowRight,
  Copy,
  Check,
  ExternalLink,
  Info,
  Trash2,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { api } from '@/lib/api';
import {
  BULK_TEMPLATE_CSV,
  MAX_BULK_ROWS,
  ingestionOutcome,
  isValidRow,
  mergeIngestionResults,
  parseBulkFile,
  toBulkPayload,
  type ParsedRow,
} from '@/lib/merchant/bulkOrders';
import { apiErrorMessage, formatLkr } from '@/lib/merchant/format';
import type { BulkIngestionResponse } from '@/types/merchant';

const OUTCOME_HEADINGS = {
  success: { text: 'All orders created', Icon: CheckCircle2, color: 'text-emerald-600' },
  partial: { text: 'Some orders were not created', Icon: AlertTriangle, color: 'text-amber-600' },
  failed: { text: 'No orders were created', Icon: XCircle, color: 'text-rose-600' },
} as const;

export default function MerchantBulkUploadPage() {
  const [parsedRows, setParsedRows] = useState<ParsedRow[]>([]);
  const [truncatedCount, setTruncatedCount] = useState(0);
  const [fileName, setFileName] = useState<string | null>(null);
  const [fileType, setFileType] = useState<'csv' | 'json' | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [summary, setSummary] = useState<BulkIngestionResponse | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [copiedTracking, setCopiedTracking] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const validCount = parsedRows.filter(isValidRow).length;
  const invalidCount = parsedRows.length - validCount;

  const handleDownloadTemplate = () => {
    const blob = new Blob([BULK_TEMPLATE_CSV], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'swiftdrop_bulk_order_template.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const resetFile = () => {
    setParsedRows([]);
    setTruncatedCount(0);
    setFileName(null);
    setFileType(null);
    setFileError(null);
    setSummary(null);
    setSubmitError(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    resetFile();
    setFileName(file.name);
    setFileType(file.name.toLowerCase().endsWith('.json') ? 'json' : 'csv');

    const reader = new FileReader();
    reader.onload = (event) => {
      const result = parseBulkFile(file.name, String(event.target?.result ?? ''));
      if (result.fileError) {
        setFileError(result.fileError);
        setFileName(null);
        if (fileInputRef.current) fileInputRef.current.value = '';
        return;
      }
      setParsedRows(result.rows);
      setTruncatedCount(result.truncatedCount);
    };
    reader.onerror = () => setFileError('The file could not be read. Try saving it again and re-uploading.');
    reader.readAsText(file);
  };

  // Only valid rows are sent: one malformed row would otherwise fail validation for the whole batch.
  const handleSubmitBatch = async () => {
    if (validCount === 0) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await api.post<BulkIngestionResponse>('/api/v1/parcels/bulk', toBulkPayload(parsedRows));
      setSummary(mergeIngestionResults(parsedRows, response.data));
    } catch (err) {
      // No simulated results: nothing was booked, so say so and let the merchant retry.
      setSubmitError(apiErrorMessage(err, 'Upload failed and no orders were created. Check your connection and try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  const copyTracking = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopiedTracking(code);
      setTimeout(() => setCopiedTracking(null), 2500);
    } catch {
      setCopiedTracking(null);
    }
  };

  const outcome = summary ? OUTCOME_HEADINGS[ingestionOutcome(summary)] : null;

  return (
    <AppShell>
      <div className="space-y-6">
        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
              <UploadCloud className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Bulk Order Ingestion</h1>
              <p className="text-sm text-slate-500">
                Book up to {MAX_BULK_ROWS} parcels at once with the 15% corporate discount
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleDownloadTemplate}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all"
            >
              <Download className="w-4 h-4 text-slate-500" />
              Download CSV Template
            </button>
            <Link
              href="/merchant/dashboard"
              className="inline-flex items-center gap-1.5 px-4 py-2.5 text-xs font-semibold text-blue-600 hover:text-blue-700 hover:bg-blue-50 rounded-xl transition-all"
            >
              Back to Dashboard &rarr;
            </Link>
          </div>
        </div>

        {fileError && (
          <div role="alert" className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-sm text-rose-800">
            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
            <p>{fileError}</p>
          </div>
        )}

        {/* Upload Zone */}
        {!summary && parsedRows.length === 0 && (
          <div className="bg-white p-8 rounded-3xl border-2 border-dashed border-slate-300 hover:border-blue-500 transition-all text-center space-y-4">
            <div className="w-16 h-16 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto shadow-inner">
              <UploadCloud className="w-8 h-8" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-900">Upload a CSV or JSON order file</h3>
              <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
                Use the template&apos;s columns. Each row is checked before anything is sent, so you can fix problems first.
              </p>
            </div>

            <input
              type="file"
              ref={fileInputRef}
              accept=".csv,.json"
              onChange={handleFileUpload}
              className="hidden"
              id="bulk-file-input"
            />
            <label
              htmlFor="bulk-file-input"
              className="inline-flex items-center gap-2 px-6 py-3 text-sm font-bold text-white bg-blue-600 hover:bg-blue-700 rounded-xl cursor-pointer shadow-md shadow-blue-500/20 transition-all"
            >
              <FileSpreadsheet className="w-4 h-4" />
              Choose File from Computer
            </label>

            <div className="flex items-center justify-center gap-4 text-xs text-slate-400 pt-2">
              <span className="flex items-center gap-1">
                <FileSpreadsheet className="w-3.5 h-3.5 text-emerald-500" /> .CSV
              </span>
              <span>&bull;</span>
              <span className="flex items-center gap-1">
                <FileCode className="w-3.5 h-3.5 text-indigo-500" /> .JSON
              </span>
              <span>&bull;</span>
              <span>Up to {MAX_BULK_ROWS} parcels</span>
            </div>
          </div>
        )}

        {submitError && (
          <div role="alert" className="p-4 bg-rose-50 border border-rose-200 rounded-2xl text-sm text-rose-800">
            {submitError}
          </div>
        )}

        {/* Preview before submission */}
        {parsedRows.length > 0 && !summary && (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden space-y-4 p-6">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-4 border-b border-slate-100">
              <div className="flex items-center gap-3 min-w-0">
                <div className="p-2 rounded-xl bg-blue-50 text-blue-600 font-bold text-xs uppercase">{fileType?.toUpperCase()}</div>
                <div className="min-w-0">
                  <h2 className="text-base font-bold text-slate-900 truncate">
                    Parsed File: <span className="font-mono text-blue-600">{fileName}</span>
                  </h2>
                  <p className="text-xs text-slate-500">
                    {parsedRows.length} rows loaded &bull; <span className="text-emerald-600 font-bold">{validCount} ready</span> &bull;{' '}
                    <span className="text-rose-600 font-bold">{invalidCount} with errors</span>
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={resetFile}
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 rounded-xl transition-all"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Discard
                </button>
                <button
                  onClick={handleSubmitBatch}
                  disabled={submitting || validCount === 0}
                  className="inline-flex items-center gap-2 px-6 py-2 text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-xl shadow-md shadow-blue-500/20 transition-all"
                >
                  {submitting ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Creating orders...
                    </>
                  ) : (
                    <>
                      Create {validCount} {validCount === 1 ? 'Order' : 'Orders'} <ArrowRight className="w-3.5 h-3.5" />
                    </>
                  )}
                </button>
              </div>
            </div>

            {truncatedCount > 0 && (
              <div role="alert" className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-amber-600" />
                <span>
                  Only the first {MAX_BULK_ROWS} rows were loaded. {truncatedCount} more {truncatedCount === 1 ? 'row was' : 'rows were'} left
                  out; upload them in another file.
                </span>
              </div>
            )}

            {invalidCount > 0 && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
                <span>
                  {invalidCount} {invalidCount === 1 ? 'row has' : 'rows have'} errors and will not be sent. Fix the file and upload it again,
                  or create the ready rows now.
                </span>
              </div>
            )}

            <div className="p-3 bg-blue-50 border border-blue-200 rounded-xl text-xs text-blue-800 flex items-center gap-2">
              <Info className="w-4 h-4 shrink-0 text-blue-600" />
              <span>Each created order gets a tracking number (SD-YYYYMMDD-XXXXXX) and the 15% corporate discount.</span>
            </div>

            <div className="overflow-x-auto max-h-[420px]">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 text-slate-500 uppercase font-semibold">
                  <tr>
                    <th className="py-2.5 px-3">#</th>
                    <th className="py-2.5 px-4">Recipient</th>
                    <th className="py-2.5 px-4">Destination</th>
                    <th className="py-2.5 px-3">Weight</th>
                    <th className="py-2.5 px-3">Size</th>
                    <th className="py-2.5 px-3">Ref</th>
                    <th className="py-2.5 px-4">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {parsedRows.map((row) => {
                    const v = row.values;
                    const valid = isValidRow(row);
                    return (
                      <tr key={row.rowNumber} className={valid ? 'hover:bg-slate-50/60' : 'bg-rose-50/30'}>
                        <td className="py-3 px-3 font-mono font-bold text-slate-400">{row.rowNumber}</td>
                        <td className="py-3 px-4">
                          <div className="font-semibold text-slate-800">{v.recipientName || '—'}</div>
                          <div className="text-2xs text-slate-400 font-mono">{v.recipientPhone || '—'}</div>
                        </td>
                        <td className="py-3 px-4 max-w-[200px] truncate text-slate-700" title={v.dropoffAddress}>
                          {v.dropoffAddress || '—'}
                        </td>
                        <td className="py-3 px-3 font-semibold text-slate-800">
                          {Number.isFinite(v.weightKg) ? `${v.weightKg} kg` : '—'}
                        </td>
                        <td className="py-3 px-3">
                          <span className="px-1.5 py-0.5 rounded-md bg-slate-100 text-slate-700 font-bold text-2xs">{v.parcelSize ?? '—'}</span>
                        </td>
                        <td className="py-3 px-3 font-mono text-slate-500">{v.merchantOrderReference || '—'}</td>
                        <td className="py-3 px-4">
                          {valid ? (
                            <span className="inline-flex items-center gap-1 text-emerald-700 font-bold">
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Ready
                            </span>
                          ) : (
                            <div className="text-rose-700 font-semibold space-y-0.5">
                              {row.errors.map((msg) => (
                                <div key={msg} className="flex items-start gap-1">
                                  <AlertCircle className="w-3.5 h-3.5 text-rose-600 shrink-0 mt-px" /> {msg}
                                </div>
                              ))}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Results */}
        {summary && outcome && (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-xs p-6 space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-100">
              <div>
                <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                  <outcome.Icon className={`w-5 h-5 ${outcome.color}`} /> {outcome.text}
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  {summary.successfulCount} of {summary.totalReceived} rows became orders. Failed rows list the reason below.
                </p>
              </div>

              <div className="flex items-center gap-2">
                <Link
                  href="/merchant/orders"
                  className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-blue-600 hover:bg-blue-50 rounded-xl transition-all"
                >
                  View orders
                </Link>
                <button
                  onClick={resetFile}
                  className="inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all"
                >
                  Upload Another Batch
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-center">
                <span className="text-xs font-semibold text-slate-500 uppercase">Rows in file</span>
                <p className="text-2xl font-extrabold text-slate-900 mt-1">{summary.totalReceived}</p>
              </div>
              <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-center">
                <span className="text-xs font-semibold text-emerald-700 uppercase">Created Orders</span>
                <p className="text-2xl font-extrabold text-emerald-700 mt-1">{summary.successfulCount}</p>
              </div>
              <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-center">
                <span className="text-xs font-semibold text-rose-700 uppercase">Failed Rows</span>
                <p className="text-2xl font-extrabold text-rose-700 mt-1">{summary.failedCount}</p>
              </div>
            </div>

            <div className="overflow-x-auto max-h-[460px]">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 text-slate-500 uppercase font-semibold">
                  <tr>
                    <th className="py-2.5 px-3">#</th>
                    <th className="py-2.5 px-4">Ref Code</th>
                    <th className="py-2.5 px-6">Tracking Number</th>
                    <th className="py-2.5 px-4">Fee (15% Disc)</th>
                    <th className="py-2.5 px-4">Status</th>
                    <th className="py-2.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {summary.results.map((item) => (
                    <tr key={item.rowNumber} className="hover:bg-slate-50/60">
                      <td className="py-3 px-3 font-mono font-bold text-slate-400">{item.rowNumber}</td>
                      <td className="py-3 px-4 font-mono text-slate-700">{item.merchantOrderReference || '—'}</td>
                      <td className="py-3 px-6">
                        {item.trackingNumber ? (
                          <div className="flex items-center gap-2 font-mono font-bold text-blue-600">
                            <span>{item.trackingNumber}</span>
                            <button
                              onClick={() => copyTracking(item.trackingNumber!)}
                              className="p-1 hover:bg-slate-100 rounded-md transition-all text-slate-400 hover:text-slate-600"
                              title="Copy Tracking Number"
                              aria-label="Copy tracking number"
                            >
                              {copiedTracking === item.trackingNumber ? (
                                <Check className="w-3.5 h-3.5 text-emerald-600" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                      <td className="py-3 px-4 font-bold text-slate-900">{formatLkr(item.estimatedFee)}</td>
                      <td className="py-3 px-4">
                        {item.status === 'SUCCESS' ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-2xs font-bold bg-emerald-100 text-emerald-800">
                            Created
                          </span>
                        ) : (
                          <div className="space-y-0.5">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-2xs font-bold bg-rose-100 text-rose-800">
                              Failed
                            </span>
                            {item.errors.map((msg) => (
                              <div key={msg} className="text-2xs text-rose-700">
                                {msg}
                              </div>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {item.trackingNumber && (
                          <Link
                            href={`/track/${encodeURIComponent(item.trackingNumber)}`}
                            target="_blank"
                            className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-800 font-semibold"
                          >
                            Track <ExternalLink className="w-3.5 h-3.5" />
                          </Link>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
