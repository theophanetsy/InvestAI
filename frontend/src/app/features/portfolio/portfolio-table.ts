import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import type { PeaEligibilityResponse, PortfolioLineResponse } from '../../api/generated';
import {
  formatAmount,
  formatDate,
  formatPercent,
  formatQuantity,
  formatSignedAmount,
} from '../../shared/format/decimal';
import { PeaEligibilityBadge } from '../../shared/pea-eligibility/pea-eligibility-badge';

const NONE = '—';

/**
 * One row as displayed. Built in TypeScript because `*matCellDef="let row"` is untyped in the
 * template: every field of the generated client is read here, under strict type checking.
 */
interface LineRow {
  readonly line: PortfolioLineResponse;
  readonly name: string;
  readonly codes: string;
  readonly eligibility: PeaEligibilityResponse;
  readonly quantity: string;
  /** `null` when no close is known ("Cours indisponible"). */
  readonly close: string | null;
  readonly closeDate: string;
  readonly value: string;
  readonly weight: string;
  readonly gain: string;
}

/** The valued lines; every figure comes from the backend as text and is only formatted here. */
@Component({
  selector: 'inv-portfolio-table',
  imports: [MatButton, MatTableModule, PeaEligibilityBadge],
  templateUrl: './portfolio-table.html',
  styleUrl: './portfolio-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioTable {
  readonly lines = input.required<readonly PortfolioLineResponse[]>();
  /** Currency of the valuation (amounts of the lines), from the portfolio. */
  readonly currency = input.required<string>();
  readonly editLine = output<PortfolioLineResponse>();
  readonly removeLine = output<PortfolioLineResponse>();
  readonly correctEligibility = output<PortfolioLineResponse>();

  protected readonly columns = [
    'instrument',
    'quantity',
    'lastClose',
    'value',
    'weight',
    'gain',
    'actions',
  ] as const;

  protected readonly rows = computed(() =>
    this.lines().map((line) => toRow(line, this.currency())),
  );

  /** Keeps the rows (and the focus restored by a dialog) across reloads. */
  protected readonly trackById = (_index: number, row: LineRow): string => row.line.id;
}

function toRow(line: PortfolioLineResponse, currency: string): LineRow {
  const { instrument, lastClose } = line;
  return {
    line,
    name: instrument.name,
    codes: `${instrument.ticker} · ${instrument.isin}`,
    eligibility: instrument.peaEligibility,
    quantity: formatQuantity(line.quantity),
    close: lastClose ? formatAmount(lastClose.close, instrument.currency) : null,
    closeDate: lastClose ? `au ${formatDate(lastClose.date)}` : '',
    value: line.value ? formatAmount(line.value, currency) : NONE,
    weight: line.weightPercent ? formatPercent(line.weightPercent) : NONE,
    gain: gainText(line, currency),
  };
}

/** `+230,37 € (+15,35 %)`, or a dash without average price or close. */
function gainText(line: PortfolioLineResponse, currency: string): string {
  if (!line.unrealizedGain) {
    return NONE;
  }
  const amount = formatSignedAmount(line.unrealizedGain, currency);
  return line.unrealizedGainPercent
    ? `${amount} (${formatPercent(line.unrealizedGainPercent, true)})`
    : amount;
}
