import type { IndicatorResponse } from '../../api/generated';
import { IndicatorResponseStatusEnum } from '../../api/generated';
import { formatAmount, formatDecimal, formatPercent } from './decimal';

/** How an available indicator is written. */
export type IndicatorKind = 'percent' | 'signedPercent' | 'points' | 'coefficient' | 'amount';

/** No-break space before a unit, built from its code (an escape typed in a tool may be lost). */
const NBSP = String.fromCharCode(0xa0);

/** Text of an indicator without value: never a zero nor an approximate figure. */
export const INSUFFICIENT_HISTORY = 'Historique insuffisant';
export const NOT_COMPUTABLE = 'Non calculable';

/**
 * An indicator computed by the backend (decimal string), formatted in French, or the reason why it
 * is missing. Nothing is computed here. An amount needs its currency (read in the data): without
 * it, the number is written alone.
 */
export function formatIndicator(
  indicator: IndicatorResponse,
  kind: IndicatorKind,
  currency?: string,
): string {
  const { value } = indicator;
  if (indicator.status !== IndicatorResponseStatusEnum.Available || value === undefined) {
    return indicator.status === IndicatorResponseStatusEnum.InsufficientHistory
      ? INSUFFICIENT_HISTORY
      : NOT_COMPUTABLE;
  }
  switch (kind) {
    case 'percent':
      return formatPercent(value);
    case 'signedPercent':
      return formatPercent(value, true);
    case 'points':
      return formatPoints(value);
    case 'amount':
      return currency === undefined ? formatDecimal(value) : formatAmount(value, currency);
    default:
      return formatDecimal(value);
  }
}

/** Percentage points, signed: `+3,10 pts`. */
function formatPoints(value: string): string {
  const sign = !value.startsWith('-') && /[1-9]/.test(value) ? '+' : '';
  return `${sign}${formatDecimal(value)}${NBSP}pts`;
}

/** Whether the backend lacked history for this indicator. */
export function isInsufficient(indicator: IndicatorResponse): boolean {
  return indicator.status === IndicatorResponseStatusEnum.InsufficientHistory;
}
