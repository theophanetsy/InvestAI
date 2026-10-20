import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';
import type { PortfolioIndicatorsResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { inlineErrors } from '../../core/http/http-context';
import { failureMessage } from '../../core/http/problem';
import { DashboardContent } from './dashboard-content';

/**
 * "Tableau de bord": indicators of the declared PEA computed by the backend on the stored closes
 * (spec 2.4, criterion 10). Every figure arrives as a decimal string: nothing is computed here.
 */
@Component({
  selector: 'inv-dashboard',
  imports: [DashboardContent, MatButton, MatProgressBar],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly api = inject(PortfolioService);

  protected readonly indicators = signal<PortfolioIndicatorsResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getPortfolioIndicators('body', false, { context: inlineErrors() })
      .pipe(
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: (indicators) => {
          this.indicators.set(indicators);
        },
        error: (error: unknown) => {
          this.loadError.set(failureMessage('Les indicateurs n’ont pas pu être chargés.', error));
        },
      });
  }
}
