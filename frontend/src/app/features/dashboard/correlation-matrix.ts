import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { CorrelationMatrixResponse, LineIndicatorsResponse } from '../../api/generated';
import { formatIndicator } from '../../shared/format/indicator';

/** Correlations between the lines kept in the risk of the portfolio, as computed by the backend. */
@Component({
  selector: 'inv-correlation-matrix',
  templateUrl: './correlation-matrix.html',
  styleUrl: './correlation-matrix.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorrelationMatrix {
  readonly matrix = input.required<CorrelationMatrixResponse>();
  /** To name the instruments of the matrix. */
  readonly lines = input.required<readonly LineIndicatorsResponse[]>();

  protected readonly names = computed(() => {
    const tickers = new Map(this.lines().map((line) => [line.instrument.id, line.instrument]));
    return this.matrix().instrumentIds.map((id) => ({
      id,
      ticker: tickers.get(id)?.ticker ?? id,
      name: tickers.get(id)?.name ?? id,
    }));
  });

  protected readonly rows = computed(() =>
    this.names().map((instrument, index) => ({
      ...instrument,
      cells: (this.matrix().rows[index] ?? []).map((cell) => formatIndicator(cell, 'coefficient')),
    })),
  );
}
