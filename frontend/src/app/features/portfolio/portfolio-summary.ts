import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { PortfolioResponse } from '../../api/generated';
import { formatAmount, formatPercent } from '../../shared/format/decimal';
import { CashForm } from './cash-form';

/** Total value (cash included), incompleteness warning and declared cash. */
@Component({
  selector: 'inv-portfolio-summary',
  imports: [CashForm],
  templateUrl: './portfolio-summary.html',
  styleUrl: './portfolio-summary.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioSummary {
  readonly portfolio = input.required<PortfolioResponse>();
  /** Emitted once the cash is stored. */
  readonly cashSaved = output();

  protected readonly total = computed(() =>
    formatAmount(this.portfolio().total, this.portfolio().currency),
  );
  protected readonly cashWeight = computed(() => {
    const weight = this.portfolio().cash?.weightPercent;
    return weight ? `Part des espèces : ${formatPercent(weight)}` : '';
  });
}
