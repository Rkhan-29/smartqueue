import { describe, expect, it } from 'vitest';
import { extractQueueRef, extractReference, formatWait } from '../utils/format';

describe('formatWait', () => {
  it('marks values as approximate', () => {
    expect(formatWait(12)).toBe('~12 min');
  });
  it('handles zero, null and hours', () => {
    expect(formatWait(0)).toBe('Under a minute');
    expect(formatWait(null)).toBe('-');
    expect(formatWait(90)).toBe('~1 hr 30 min');
  });
});

describe('extractReference', () => {
  it('accepts a bare reference', () => {
    expect(extractReference('  abc_DEF-123  ')).toBe('abc_DEF-123');
  });
  it('extracts the reference from a token link', () => {
    expect(extractReference('https://app.example.com/queue/token/xYz-9_Q')).toBe('xYz-9_Q');
  });
});

describe('extractQueueRef', () => {
  it('reads the public ref from a scanned QR link', () => {
    expect(extractQueueRef('https://app.example.com/join/AbCdEf123456')).toBe('AbCdEf123456');
  });
  it('rejects unrelated text', () => {
    expect(extractQueueRef('hello world')).toBeNull();
    expect(extractQueueRef('http://evil.example/other')).toBeNull();
  });
});
