import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { PortfolioIndicatorsResponse } from '../../api/generated';
import { CorrelationMatrix } from './correlation-matrix';
import { DashboardWarnings } from './dashboard-warnings';
import { LineIndicatorsTable } from './line-indicators-table';
import { PerformanceChart } from './performance-chart';
import { RiskCards } from './risk-cards';

/** The dashboard once the indicators are loaded: an invitation when no line is declared. */
@Component({
  selector: 'inv-dashboard-content',
  imports: [
    CorrelationMatrix,
    DashboardWarnings,
    LineIndicatorsTable,
    PerformanceChart,
    RiskCards,
    RouterLink,
  ],
  templateUrl: './dashboard-content.html',
  styleUrl: './dashboard-content.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardContent {
  readonly indicators = input.required<PortfolioIndicatorsResponse>();
}
