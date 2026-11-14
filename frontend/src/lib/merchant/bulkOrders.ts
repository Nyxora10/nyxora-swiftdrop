/**
 * Bulk order file handling for the merchant portal (Member 2, Task 1.13).
 *
 * Parses CSV or JSON order files in the browser, validates each row with the same rules
 * parcel-service applies, and merges the server's per-row results back onto the file's rows.
 */
import type {
  BulkIngestionResponse,
  BulkOrderInput,
  BulkResultItem,
  ParcelSize,
} from '@/types/merchant';

/** parcel-service rejects batches larger than this (BulkOrderRequest @Size(max = 100)). */
export const MAX_BULK_ROWS = 100;

/** Same pattern as MerchantOrderRequest.recipientPhone on the server. */
export const PHONE_PATTERN = /^\+?[0-9]{9,15}$/;

export const PARCEL_SIZES: readonly ParcelSize[] = ['SMALL', 'MEDIUM', 'LARGE'];

/** Column order of the downloadable template; also the positional order for header-less files. */
export const BULK_COLUMNS = [
  'originZoneId',
  'destinationZoneId',
  'pickupAddress',
  'dropoffAddress',
  'recipientName',
  'recipientPhone',
  'weightKg',
  'parcelSize',
  'merchantOrderReference',
] as const;

type BulkColumn = (typeof BULK_COLUMNS)[number];

export const BULK_TEMPLATE_CSV =
  BULK_COLUMNS.join(',') +
  '\n' +
  '1,2,"Peliyagoda Warehouse 4","No. 42 Peradeniya Road, Kandy","Kasun Jayawardena","+94771234567",2.5,MEDIUM,DARAZ-101\n' +
  '1,3,"Peliyagoda Warehouse 4","Main Street, Galle Fort, Galle","Anura Senanayake","+94719876543",1.2,SMALL,DARAZ-102\n' +
  '1,4,"Peliyagoda Warehouse 4","Kurunegala Town, Kurunegala","Dilini Wickramasinghe","+94785556677",4.8,LARGE,DARAZ-103\n' +
  '1,5,"Peliyagoda Warehouse 4","Hospital Road, Jaffna","Mahesh Gunathilaka","+94701112233",1.0,SMALL,DARAZ-104\n';

export interface ParsedRow {
  /** 1-based data row number in the file (the CSV header is not counted). */
  rowNumber: number;
  /** Normalised values. Invalid numbers are NaN and invalid sizes are null; check errors first. */
  values: Omit<BulkOrderInput, 'parcelSize'> & { parcelSize: ParcelSize | null };
  errors: string[];
}

export interface ParseResult {
  rows: ParsedRow[];
  /** Data rows after the first MAX_BULK_ROWS that were not loaded. */
  truncatedCount: number;
  /** Set when the file as a whole cannot be used; rows is then empty. */
  fileError?: string;
}

export const isValidRow = (row: ParsedRow): boolean => row.errors.length === 0;

/**
 * Splits CSV text into records following RFC 4180: quoted fields may contain commas,
 * line breaks and doubled quotes (""). Fully blank lines are dropped.
 */
export function parseCsvRecords(csv: string): string[][] {
  const records: string[][] = [];
  let record: string[] = [];
  let field = '';
  let inQuotes = false;
  let i = 0;
  const input = csv.charCodeAt(0) === 0xfeff ? csv.slice(1) : csv; // strip a UTF-8 BOM

  const endRecord = () => {
    record.push(field);
    if (record.length > 1 || record[0].trim() !== '') records.push(record);
    record = [];
    field = '';
  };

  while (i < input.length) {
    const ch = input[i];
    if (inQuotes) {
      if (ch === '"') {
        if (input[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
      } else {
        field += ch;
      }
      i += 1;
      continue;
    }
    if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      record.push(field);
      field = '';
    } else if (ch === '\n' || ch === '\r') {
      endRecord();
      if (ch === '\r' && input[i + 1] === '\n') i += 1;
    } else {
      field += ch;
    }
    i += 1;
  }
  if (field !== '' || record.length > 0) endRecord();
  return records;
}

const text = (value: unknown): string => (value === undefined || value === null ? '' : String(value).trim());

function toNumber(value: unknown): number {
  const raw = text(value);
  return raw === '' ? NaN : Number(raw);
}

/** Validates one order with the server's rules and returns normalised values plus every error found. */
export function validateOrder(raw: Partial<Record<BulkColumn, unknown>>, rowNumber: number): ParsedRow {
  const errors: string[] = [];

  const originZoneId = toNumber(raw.originZoneId);
  const destinationZoneId = toNumber(raw.destinationZoneId);
  const pickupAddress = text(raw.pickupAddress);
  const dropoffAddress = text(raw.dropoffAddress);
  const recipientName = text(raw.recipientName);
  // Spaces and dashes are common in typed phone numbers; the server accepts digits only.
  const recipientPhone = text(raw.recipientPhone).replace(/[\s-]/g, '');
  const weightKg = toNumber(raw.weightKg);
  const sizeText = text(raw.parcelSize).toUpperCase();
  const parcelSize = (PARCEL_SIZES as readonly string[]).includes(sizeText) ? (sizeText as ParcelSize) : null;
  const reference = text(raw.merchantOrderReference);

  if (!Number.isInteger(originZoneId) || originZoneId <= 0) errors.push('Origin zone ID must be a whole number above 0');
  if (!Number.isInteger(destinationZoneId) || destinationZoneId <= 0)
    errors.push('Destination zone ID must be a whole number above 0');
  if (!pickupAddress) errors.push('Pickup address is missing');
  if (!dropoffAddress) errors.push('Dropoff address is missing');
  if (!recipientName) errors.push('Recipient name is missing');
  if (!recipientPhone) errors.push('Recipient phone is missing');
  else if (!PHONE_PATTERN.test(recipientPhone))
    errors.push('Recipient phone must be 9 to 15 digits, optionally starting with +');
  if (!Number.isFinite(weightKg) || weightKg < 0.1) errors.push('Weight must be at least 0.1 kg');
  if (!parcelSize) errors.push('Size must be SMALL, MEDIUM or LARGE');

  return {
    rowNumber,
    values: {
      originZoneId,
      destinationZoneId,
      pickupAddress,
      dropoffAddress,
      recipientName,
      recipientPhone,
      weightKg,
      parcelSize,
      merchantOrderReference: reference || undefined,
    },
    errors,
  };
}

function limitRows<T>(items: T[]): { kept: T[]; truncatedCount: number } {
  return {
    kept: items.slice(0, MAX_BULK_ROWS),
    truncatedCount: Math.max(0, items.length - MAX_BULK_ROWS),
  };
}

/**
 * Parses a bulk CSV file. A header row is matched by column name (any order, any case);
 * a file without a recognisable header is read in template column order.
 */
export function parseBulkCsv(content: string): ParseResult {
  const records = parseCsvRecords(content);
  if (records.length === 0) {
    return { rows: [], truncatedCount: 0, fileError: 'The file is empty.' };
  }

  const normalise = (h: string) => h.trim().toLowerCase();
  const header = records[0].map(normalise);
  const known = BULK_COLUMNS.map((c) => c.toLowerCase());
  const hasHeader = header.some((h) => known.includes(h));

  let columnIndex: Record<BulkColumn, number>;
  if (hasHeader) {
    const missing = BULK_COLUMNS.filter((c) => c !== 'merchantOrderReference' && !header.includes(c.toLowerCase()));
    if (missing.length > 0) {
      return {
        rows: [],
        truncatedCount: 0,
        fileError: `The header row is missing these columns: ${missing.join(', ')}. Download the template to see the expected format.`,
      };
    }
    columnIndex = Object.fromEntries(BULK_COLUMNS.map((c) => [c, header.indexOf(c.toLowerCase())])) as Record<
      BulkColumn,
      number
    >;
  } else {
    columnIndex = Object.fromEntries(BULK_COLUMNS.map((c, i) => [c, i])) as Record<BulkColumn, number>;
  }

  const dataRecords = hasHeader ? records.slice(1) : records;
  if (dataRecords.length === 0) {
    return { rows: [], truncatedCount: 0, fileError: 'The file has a header row but no orders.' };
  }

  const { kept, truncatedCount } = limitRows(dataRecords);
  const rows = kept.map((cells, idx) => {
    const raw: Partial<Record<BulkColumn, unknown>> = {};
    for (const column of BULK_COLUMNS) {
      const at = columnIndex[column];
      raw[column] = at >= 0 ? cells[at] : undefined;
    }
    const row = validateOrder(raw, idx + 1);
    if (!hasHeader && cells.length < BULK_COLUMNS.length - 1) {
      row.errors.unshift(`Expected at least ${BULK_COLUMNS.length - 1} columns, found ${cells.length}`);
    }
    return row;
  });

  return { rows, truncatedCount };
}

/** Parses a bulk JSON file: an array of orders, or an object with an "orders" array. */
export function parseBulkJson(content: string): ParseResult {
  let data: unknown;
  try {
    data = JSON.parse(content);
  } catch {
    return { rows: [], truncatedCount: 0, fileError: 'The file is not valid JSON.' };
  }

  const items =
    Array.isArray(data)
      ? data
      : data && typeof data === 'object' && Array.isArray((data as { orders?: unknown }).orders)
        ? (data as { orders: unknown[] }).orders
        : null;

  if (!items) {
    return {
      rows: [],
      truncatedCount: 0,
      fileError: 'Expected a JSON array of orders, or an object with an "orders" array.',
    };
  }
  if (items.length === 0) {
    return { rows: [], truncatedCount: 0, fileError: 'The file contains no orders.' };
  }

  const { kept, truncatedCount } = limitRows(items);
  const rows = kept.map((item, idx) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      const row = validateOrder({}, idx + 1);
      row.errors = ['Order must be a JSON object'];
      return row;
    }
    return validateOrder(item as Partial<Record<BulkColumn, unknown>>, idx + 1);
  });

  return { rows, truncatedCount };
}

/** Picks the parser from the file name. */
export function parseBulkFile(fileName: string, content: string): ParseResult {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.csv')) return parseBulkCsv(content);
  if (lower.endsWith('.json')) return parseBulkJson(content);
  return { rows: [], truncatedCount: 0, fileError: 'Unsupported file type. Upload a .csv or .json file.' };
}

/** Builds the POST /api/v1/parcels/bulk body from the valid rows only. */
export function toBulkPayload(rows: ParsedRow[]): { orders: BulkOrderInput[] } {
  return {
    orders: rows.filter(isValidRow).map((r) => ({ ...r.values, parcelSize: r.values.parcelSize as ParcelSize })),
  };
}

/**
 * Combines the server's per-row results with the rows rejected in the browser.
 * The server numbers rows within the submitted batch, so they are mapped back to file rows.
 */
export function mergeIngestionResults(rows: ParsedRow[], response: BulkIngestionResponse): BulkIngestionResponse {
  const submitted = rows.filter(isValidRow);
  const serverResults: BulkResultItem[] = response.results.map((item) => ({
    ...item,
    errors: item.errors ?? [],
    rowNumber: submitted[item.rowNumber - 1]?.rowNumber ?? item.rowNumber,
  }));
  const rejectedLocally: BulkResultItem[] = rows
    .filter((r) => !isValidRow(r))
    .map((r) => ({
      rowNumber: r.rowNumber,
      trackingNumber: null,
      estimatedFee: null,
      status: 'FAILED',
      merchantOrderReference: r.values.merchantOrderReference,
      errors: r.errors,
    }));

  return {
    totalReceived: rows.length,
    successfulCount: response.successfulCount,
    failedCount: response.failedCount + rejectedLocally.length,
    results: [...serverResults, ...rejectedLocally].sort((a, b) => a.rowNumber - b.rowNumber),
  };
}

export type IngestionOutcome = 'success' | 'partial' | 'failed';

export function ingestionOutcome(summary: Pick<BulkIngestionResponse, 'successfulCount' | 'failedCount'>): IngestionOutcome {
  if (summary.successfulCount === 0) return 'failed';
  return summary.failedCount > 0 ? 'partial' : 'success';
}
