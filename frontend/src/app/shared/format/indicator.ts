import type { IndicatorResponse } from '../../api/generated';
import { IndicatorResponseStatusEnum } from '../../api/generated';
import { formatAmount, formatDecimal, formatPercent } from './decimal';

/** How an available indicator is written. */
export type IndicatorKind = 'percent' | 'signedPercent' | 'coefficient' | 'amount';

/** Text of an indicator without value: never a zero nor an approximate figure. */
export const INSUFFICIENT_HISTORY = 'Historique insuffisant';
export const NOT_COMPUTABLE = 'Non calculable';

/**
 * An indicator computed by the backend (decimal string), formatted in French, or the reason why it
 * is missing. Nothing is computed here.
 */
export function formatIndicator(
  indicator: IndicatorResponse,
  kind: IndicatorKind,
  currency = 'EUR',
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
    case 'amount':
      return formatAmount(value, currency);
    default:
      return formatDecimal(value);
  }
}

/** Whether the backend lacked history for this indicator. */
export function isInsufficient(indicator: IndicatorResponse): boolean {
  return indicator.status === IndicatorResponseStatusEnum.InsufficientHistory;
}
