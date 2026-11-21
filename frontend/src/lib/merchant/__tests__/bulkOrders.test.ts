import { describe, expect, it } from 'vitest';
import {
  BULK_TEMPLATE_CSV,
  MAX_BULK_ROWS,
  ingestionOutcome,
  isValidRow,
  mergeIngestionResults,
  parseBulkCsv,
  parseBulkFile,
  parseBulkJson,
  parseCsvRecords,
  toBulkPayload,
  validateOrder,
} from '../bulkOrders';

const HEADER =
  'originZoneId,destinationZoneId,pickupAddress,dropoffAddress,recipientName,recipientPhone,weightKg,parcelSize,merchantOrderReference';
const validRow = (ref: string) => `1,2,"Warehouse 4","No. 42 Road, Kandy","Kasun","+94771234567",2.5,MEDIUM,${ref}`;

describe('parseCsvRecords', () => {
  it('keeps commas, doubled quotes and line breaks inside quoted fields', () => {
    const records = parseCsvRecords('a,"b, c","say ""hi""","line1\nline2"\r\nx,y,z,w\n');
    expect(records).toEqual([
      ['a', 'b, c', 'say "hi"', 'line1\nline2'],
      ['x', 'y', 'z', 'w'],
    ]);
  });

  it('drops blank lines and a UTF-8 BOM', () => {
    expect(parseCsvRecords('﻿a,b\n\n   \nc,d')).toEqual([
      ['a', 'b'],
      ['c', 'd'],
    ]);
  });
});

describe('validateOrder', () => {
  const base = {
    originZoneId: '1',
    destinationZoneId: '2',
    pickupAddress: 'Warehouse',
    dropoffAddress: 'Kandy',
    recipientName: 'Kasun',
    recipientPhone: '+94771234567',
    weightKg: '2.5',
    parcelSize: 'medium',
  };

  it('accepts a complete order and normalises size and numbers', () => {
    const row = validateOrder(base, 1);
    expect(row.errors).toEqual([]);
    expect(row.values).toMatchObject({ originZoneId: 1, weightKg: 2.5, parcelSize: 'MEDIUM' });
    expect(row.values.merchantOrderReference).toBeUndefined();
  });

  it('strips spaces and dashes from phone numbers before checking them', () => {
    const row = validateOrder({ ...base, recipientPhone: '077 123-4567' }, 1);
    expect(row.errors).toEqual([]);
    expect(row.values.recipientPhone).toBe('0771234567');
  });

  it('rejects a phone number the server would reject', () => {
    const row = validateOrder({ ...base, recipientPhone: '12345' }, 1);
    expect(row.errors).toContain('Recipient phone must be 9 to 15 digits, optionally starting with +');
  });

  it('reports every problem instead of filling in defaults', () => {
    const row = validateOrder({ originZoneId: 'abc', weightKg: '0.05', parcelSize: 'HUGE' }, 3);
    expect(row.rowNumber).toBe(3);
    expect(Number.isNaN(row.values.originZoneId)).toBe(true);
    expect(row.values.parcelSize).toBeNull();
    expect(row.errors).toEqual(
      expect.arrayContaining([
        'Origin zone ID must be a whole number above 0',
        'Destination zone ID must be a whole number above 0',
        'Pickup address is missing',
        'Recipient phone is missing',
        'Weight must be at least 0.1 kg',
        'Size must be SMALL, MEDIUM or LARGE',
      ]),
    );
  });

  it('rejects fractional zone IDs', () => {
    expect(validateOrder({ ...base, originZoneId: '1.5' }, 1).errors).toContain(
      'Origin zone ID must be a whole number above 0',
    );
  });
});

describe('parseBulkCsv', () => {
  it('parses the downloadable template with every row valid', () => {
    const { rows, truncatedCount, fileError } = parseBulkCsv(BULK_TEMPLATE_CSV);
    expect(fileError).toBeUndefined();
    expect(truncatedCount).toBe(0);
    expect(rows).toHaveLength(4);
    expect(rows.every(isValidRow)).toBe(true);
    expect(rows[0].values.dropoffAddress).toBe('No. 42 Peradeniya Road, Kandy');
  });

  it('matches header columns by name in any order', () => {
    const csv = 'parcelSize,weightKg,recipientPhone,recipientName,dropoffAddress,pickupAddress,destinationZoneId,originZoneId\n' +
      'SMALL,1,0771234567,Nimal,Galle,Colombo,3,1\n';
    const { rows } = parseBulkCsv(csv);
    expect(rows[0].errors).toEqual([]);
    expect(rows[0].values).toMatchObject({ originZoneId: 1, destinationZoneId: 3, parcelSize: 'SMALL' });
  });

  it('reads a file without a header in template order', () => {
    const { rows } = parseBulkCsv(validRow('R-1'));
    expect(rows).toHaveLength(1);
    expect(rows[0].errors).toEqual([]);
    expect(rows[0].values.merchantOrderReference).toBe('R-1');
  });

  it('shows short rows as errors instead of dropping them', () => {
    const { rows } = parseBulkCsv(`${HEADER}\n${validRow('A')}\n1,2,Warehouse\n`);
    expect(rows).toHaveLength(2);
    expect(rows[1].rowNumber).toBe(2);
    expect(rows[1].errors).toContain('Dropoff address is missing');
  });

  it('flags header-less rows with too few columns', () => {
    const { rows } = parseBulkCsv('1,2,Warehouse');
    expect(rows[0].errors[0]).toBe('Expected at least 8 columns, found 3');
  });

  it('loads at most 100 rows and reports how many were cut off', () => {
    const lines = Array.from({ length: MAX_BULK_ROWS + 7 }, (_, i) => validRow(`R-${i + 1}`));
    const { rows, truncatedCount } = parseBulkCsv(`${HEADER}\n${lines.join('\n')}`);
    expect(rows).toHaveLength(MAX_BULK_ROWS);
    expect(truncatedCount).toBe(7);
  });

  it('explains a header that is missing required columns', () => {
    const { rows, fileError } = parseBulkCsv('originZoneId,destinationZoneId\n1,2');
    expect(rows).toEqual([]);
    expect(fileError).toContain('pickupAddress');
    expect(fileError).not.toContain('merchantOrderReference');
  });

  it('explains empty files', () => {
    expect(parseBulkCsv('').fileError).toBe('The file is empty.');
    expect(parseBulkCsv(`${HEADER}\n`).fileError).toBe('The file has a header row but no orders.');
  });
});

describe('parseBulkJson', () => {
  const order = {
    originZoneId: 1,
    destinationZoneId: 2,
    pickupAddress: 'Warehouse',
    dropoffAddress: 'Kandy',
    recipientName: 'Kasun',
    recipientPhone: '0771234567',
    weightKg: 1.2,
    parcelSize: 'SMALL',
  };

  it('accepts an array or an object with an orders array', () => {
    expect(parseBulkJson(JSON.stringify([order])).rows[0].errors).toEqual([]);
    expect(parseBulkJson(JSON.stringify({ orders: [order, order] })).rows).toHaveLength(2);
  });

  it('marks non-object entries as invalid rows', () => {
    const { rows } = parseBulkJson(JSON.stringify([order, 'oops']));
    expect(rows[1].errors).toEqual(['Order must be a JSON object']);
  });

  it('explains invalid and empty JSON', () => {
    expect(parseBulkJson('{not json').fileError).toBe('The file is not valid JSON.');
    expect(parseBulkJson('[]').fileError).toBe('The file contains no orders.');
    expect(parseBulkJson('{"items": []}').fileError).toContain('"orders" array');
  });

  it('truncates past 100 orders', () => {
    const { rows, truncatedCount } = parseBulkJson(JSON.stringify(Array(MAX_BULK_ROWS + 1).fill(order)));
    expect(rows).toHaveLength(MAX_BULK_ROWS);
    expect(truncatedCount).toBe(1);
  });
});

describe('parseBulkFile', () => {
  it('rejects other file types', () => {
    expect(parseBulkFile('orders.xlsx', '').fileError).toBe('Unsupported file type. Upload a .csv or .json file.');
  });

  it('picks the parser by extension, ignoring case', () => {
    expect(parseBulkFile('ORDERS.CSV', BULK_TEMPLATE_CSV).rows).toHaveLength(4);
  });
});

describe('submitting and merging results', () => {
  const csv = `${HEADER}\n${validRow('A')}\n1,2,Warehouse\n${validRow('C')}\n`;
  const { rows } = parseBulkCsv(csv);

  it('sends only valid rows', () => {
    const payload = toBulkPayload(rows);
    expect(payload.orders.map((o) => o.merchantOrderReference)).toEqual(['A', 'C']);
  });

  it('maps server row numbers back to file rows and adds local rejects', () => {
    const merged = mergeIngestionResults(rows, {
      totalReceived: 2,
      successfulCount: 1,
      failedCount: 1,
      results: [
        { rowNumber: 1, trackingNumber: 'SD-20261114-AAAAAA', estimatedFee: 518.5, status: 'SUCCESS', errors: [] },
        { rowNumber: 2, trackingNumber: null, estimatedFee: null, status: 'FAILED', errors: ['Zone 2 is inactive'] },
      ],
    });

    expect(merged.totalReceived).toBe(3);
    expect(merged.successfulCount).toBe(1);
    expect(merged.failedCount).toBe(2);
    expect(merged.results.map((r) => [r.rowNumber, r.status])).toEqual([
      [1, 'SUCCESS'],
      [2, 'FAILED'],
      [3, 'FAILED'],
    ]);
    expect(merged.results[1].errors).toContain('Dropoff address is missing');
    expect(merged.results[2].errors).toEqual(['Zone 2 is inactive']);
  });
});

describe('ingestionOutcome', () => {
  it('distinguishes full success, partial success and total failure', () => {
    expect(ingestionOutcome({ successfulCount: 3, failedCount: 0 })).toBe('success');
    expect(ingestionOutcome({ successfulCount: 2, failedCount: 1 })).toBe('partial');
    expect(ingestionOutcome({ successfulCount: 0, failedCount: 3 })).toBe('failed');
  });
});
