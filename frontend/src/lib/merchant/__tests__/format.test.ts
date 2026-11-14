import { describe, expect, it } from 'vitest';
import { apiErrorMessage, formatLkr, isValidWebhookUrl } from '../format';

describe('formatLkr', () => {
  it('formats amounts with two decimals and grouping', () => {
    expect(formatLkr(1830)).toBe('LKR 1,830.00');
    expect(formatLkr('91.5')).toBe('LKR 91.50');
  });

  it('shows a dash for missing or non-numeric values', () => {
    expect(formatLkr(null)).toBe('—');
    expect(formatLkr(undefined)).toBe('—');
    expect(formatLkr('')).toBe('—');
    expect(formatLkr('abc')).toBe('—');
  });
});

describe('isValidWebhookUrl', () => {
  it('accepts http and https URLs and an empty value', () => {
    expect(isValidWebhookUrl('https://store.lk/hooks/swiftdrop')).toBe(true);
    expect(isValidWebhookUrl('http://localhost:8083/api/v1/webhooks/demo-receiver')).toBe(true);
    expect(isValidWebhookUrl('   ')).toBe(true);
  });

  it('rejects other schemes and malformed input', () => {
    expect(isValidWebhookUrl('ftp://store.lk/hook')).toBe(false);
    expect(isValidWebhookUrl('store.lk/hook')).toBe(false);
    expect(isValidWebhookUrl('javascript:alert(1)')).toBe(false);
  });
});

describe('apiErrorMessage', () => {
  it('prefers the problem-detail text, then message, then the fallback', () => {
    expect(apiErrorMessage({ response: { data: { detail: 'Zone 9 not found' } } }, 'x')).toBe('Zone 9 not found');
    expect(apiErrorMessage({ response: { data: { message: 'Bad request' } } }, 'x')).toBe('Bad request');
    expect(apiErrorMessage(new Error('network'), 'Could not load')).toBe('Could not load');
  });
});
