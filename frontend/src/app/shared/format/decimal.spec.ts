import { FormControl } from '@angular/forms';
import {
  decimalValidator,
  formatAmount,
  formatDate,
  formatDecimal,
  formatPercent,
  formatQuantity,
  formatSignedAmount,
  normalizeDecimal,
} from './decimal';

const NNBSP = '\u202f';
const NBSP = '\u00a0';

describe('formatDecimal', () => {
  it('groups thousands and uses a decimal comma, without any float conversion', () => {
    expect(formatDecimal('1234567.5')).toBe(`1${NNBSP}234${NNBSP}567,50`);
    expect(formatDecimal('9007199254740993.01')).toBe(
      `9${NNBSP}007${NNBSP}199${NNBSP}254${NNBSP}740${NNBSP}993,01`,
    );
    expect(formatDecimal('0.1')).toBe('0,10');
  });

  it('keeps significant decimals beyond two and drops trailing zeros', () => {
    expect(formatDecimal('173.1600')).toBe('173,16');
    expect(formatDecimal('0.1234')).toBe('0,1234');
    expect(formatDecimal('12')).toBe('12,00');
  });

  it('keeps the sign', () => {
    expect(formatDecimal('-107.36')).toBe('-107,36');
  });
});

describe('amount formats', () => {
  it('suffixes the currency', () => {
    expect(formatAmount('1731.60', 'EUR')).toBe(`1${NNBSP}731,60${NBSP}€`);
    expect(formatAmount('10', 'USD')).toBe(`10,00${NBSP}USD`);
  });

  it('signs gains', () => {
    expect(formatSignedAmount('230.37', 'EUR')).toBe(`+230,37${NBSP}€`);
    expect(formatSignedAmount('-1.00', 'EUR')).toBe(`-1,00${NBSP}€`);
    expect(formatSignedAmount('0.00', 'EUR')).toBe(`0,00${NBSP}€`);
  });

  it('formats percentages, quantities and dates', () => {
    expect(formatPercent('86.58')).toBe(`86,58${NBSP}%`);
    expect(formatPercent('-13.42', true)).toBe(`-13,42${NBSP}%`);
    expect(formatPercent('15.35', true)).toBe(`+15,35${NBSP}%`);
    expect(formatQuantity(1234567)).toBe(`1${NNBSP}234${NNBSP}567`);
    expect(formatDate('2026-10-16')).toBe('16/10/2026');
  });
});

describe('normalizeDecimal', () => {
  it('accepts French input', () => {
    expect(normalizeDecimal(' 20 000,50 ', 'amount')).toBe('20000.50');
    expect(normalizeDecimal(`20${NNBSP}000`, 'amount')).toBe('20000');
    expect(normalizeDecimal('150.1234', 'price')).toBe('150.1234');
  });

  it('rejects anything else', () => {
    for (const input of ['', 'abc', '1,234,5', '-3', '1e3', '10.001', '1'.repeat(14)]) {
      expect(normalizeDecimal(input, 'amount')).toBeNull();
    }
    expect(normalizeDecimal('1.00001', 'price')).toBeNull();
  });
});

describe('decimalValidator', () => {
  it('accepts an empty optional value and a valid one', () => {
    const validator = decimalValidator('price', { positive: true });

    expect(validator(new FormControl(''))).toBeNull();
    expect(validator(new FormControl('12,5'))).toBeNull();
  });

  it('flags a malformed value and a zero that must be positive', () => {
    const positive = decimalValidator('amount', { positive: true });
    const zeroAllowed = decimalValidator('amount', { positive: false });

    expect(positive(new FormControl('12,345'))).toEqual({ decimal: true });
    expect(positive(new FormControl('0,00'))).toEqual({ positive: true });
    expect(zeroAllowed(new FormControl('0'))).toBeNull();
  });
});
