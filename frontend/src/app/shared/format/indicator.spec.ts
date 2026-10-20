import type { IndicatorResponse } from '../../api/generated';
import { IndicatorResponseStatusEnum } from '../../api/generated';
import { formatIndicator, isInsufficient } from './indicator';

const NBSP = '\u00a0';
const NNBSP = '\u202f';

describe('formatIndicator', () => {
  const available = (value: string): IndicatorResponse => ({
    status: IndicatorResponseStatusEnum.Available,
    value,
  });

  it('formats the decimal strings of the backend in French', () => {
    expect(formatIndicator(available('18.40'), 'percent')).toBe(`18,40${NBSP}%`);
    expect(formatIndicator(available('2.35'), 'signedPercent')).toBe(`+2,35${NBSP}%`);
    expect(formatIndicator(available('-12.30'), 'signedPercent')).toBe(`-12,30${NBSP}%`);
    expect(formatIndicator(available('0.5359'), 'coefficient')).toBe('0,5359');
    expect(formatIndicator(available('-1.00'), 'coefficient')).toBe('-1,00');
    expect(formatIndicator(available('1234567.89'), 'amount')).toBe(
      `1${NNBSP}234${NNBSP}567,89${NBSP}€`,
    );
    expect(formatIndicator(available('1000.00'), 'amount', 'USD')).toBe(
      `1${NNBSP}000,00${NBSP}USD`,
    );
  });

  it('says why a value is missing, never shows zero', () => {
    const insufficient = { status: IndicatorResponseStatusEnum.InsufficientHistory };
    const undefinedValue = { status: IndicatorResponseStatusEnum.NotComputable };

    expect(formatIndicator(insufficient, 'percent')).toBe('Historique insuffisant');
    expect(formatIndicator(undefinedValue, 'coefficient')).toBe('Non calculable');
    expect(isInsufficient(insufficient)).toBe(true);
    expect(isInsufficient(undefinedValue)).toBe(false);
  });
});
