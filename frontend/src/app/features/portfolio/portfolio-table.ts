import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import type { PortfolioLineResponse } from '../../api/generated';
import {
  formatAmount,
  formatDate,
  formatPercent,
  formatQuantity,
  formatSignedAmount,
} from '../../shared/format/decimal';
import { PeaEligibilityBadge } from '../../shared/pea-eligibility/pea-eligibility-badge';

const NONE = '—';

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

  protected readonly formatDate = formatDate;

  protected quantity(line: PortfolioLineResponse): string {
    return formatQuantity(line.quantity);
  }

  protected close(line: PortfolioLineResponse): string {
    return line.lastClose ? formatAmount(line.lastClose.close, 'EUR') : NONE;
  }

  protected value(line: PortfolioLineResponse): string {
    return line.value ? formatAmount(line.value, 'EUR') : NONE;
  }

  protected weight(line: PortfolioLineResponse): string {
    return line.weightPercent ? formatPercent(line.weightPercent) : NONE;
  }

  /** `+230,37 € (+15,35 %)`, or a dash without average price or close. */
  protected gain(line: PortfolioLineResponse): string {
    if (!line.unrealizedGain) {
      return NONE;
    }
    const amount = formatSignedAmount(line.unrealizedGain, 'EUR');
    return line.unrealizedGainPercent
      ? `${amount} (${formatPercent(line.unrealizedGainPercent, true)})`
      : amount;
  }
}
