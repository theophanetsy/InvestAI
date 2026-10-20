import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Amounts and prices travel as decimal strings (`"1731.60"`, `"173.1600"`): they are formatted and
 * parsed as text, never converted to a binary `number` (no rounding error, nothing computed on the
 * front end). French formatting: narrow no-break space between thousands, decimal comma.
 */
const THOUSANDS = '\u202f';
const NBSP = '\u00a0';
const GROUPS = /\B(?=(\d{3})+(?!\d))/g;
const SPACES = /[\s\u00a0\u202f]/g;
const TRAILING_ZEROS = /0+$/;

/** `"1234.5"` → `"1 234,50"`: at least two decimals, the significant ones beyond. */
export function formatDecimal(value: string): string {
  const negative = value.startsWith('-');
  const [integer = '0', fraction = ''] = (negative ? value.slice(1) : value).split('.');
  const decimals = fraction.replace(TRAILING_ZEROS, '').padEnd(2, '0');
  return `${negative ? '-' : ''}${integer.replace(GROUPS, THOUSANDS)},${decimals}`;
}

/** `"1731.60"`, `"EUR"` → `"1 731,60 €"`. */
export function formatAmount(value: string, currency: string): string {
  return `${formatDecimal(value)}${NBSP}${currency === 'EUR' ? '€' : currency}`;
}

/** A gain or a loss: `+230,37 €`, `-107,36 €`. */
export function formatSignedAmount(value: string, currency: string): string {
  return `${plusSign(value)}${formatAmount(value, currency)}`;
}

/** `"86.58"` → `"86,58 %"`, signed on demand. */
export function formatPercent(value: string, signed = false): string {
  return `${signed ? plusSign(value) : ''}${formatDecimal(value)}${NBSP}%`;
}

/** Whole number of units, grouped by thousands. */
export function formatQuantity(quantity: number): string {
  return String(quantity).replace(GROUPS, THOUSANDS);
}

/** ISO date of a session (`2026-10-16`) → `16/10/2026`. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-');
  return `${day ?? ''}/${month ?? ''}/${year ?? ''}`;
}

function plusSign(value: string): string {
  return !value.startsWith('-') && /[1-9]/.test(value) ? '+' : '';
}

/** Amount in euros (2 decimals) or price (4 decimals, as stored by the backend). */
export type DecimalKind = 'amount' | 'price';

const FORMATS: Record<DecimalKind, RegExp> = {
  amount: /^\d{1,13}(\.\d{1,2})?$/,
  price: /^\d{1,15}(\.\d{1,4})?$/,
};

/**
 * What the user typed (`" 20 000,50"`) as the decimal string the API expects (`"20000.50"`), or
 * `null` when it is not a non-negative decimal of this kind.
 */
export function normalizeDecimal(input: string, kind: DecimalKind): string | null {
  const compact = input.replace(SPACES, '').replace(',', '.');
  return FORMATS[kind].test(compact) ? compact : null;
}

/**
 * Validates a decimal field typed in French; an empty value is left to `required`.
 * Errors: `decimal` (malformed), `positive` (zero where a positive amount is required).
 */
export function decimalValidator(kind: DecimalKind, options: { positive: boolean }): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    const normalized = normalizeDecimal(value, kind);
    if (normalized === null) {
      return { decimal: true };
    }
    return options.positive && !/[1-9]/.test(normalized) ? { positive: true } : null;
  };
}

/** A whole number of units (quantity): `integer` error otherwise; an empty value is left to `required`. */
export function integerValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value === 'number' && !Number.isInteger(value) ? { integer: true } : null;
}
