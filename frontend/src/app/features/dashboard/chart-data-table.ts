import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { CurvePointResponse } from '../../api/generated';
import { formatDate, formatDecimal } from '../../shared/format/decimal';

const GAP = '—';

/** The values of the curve as a table: the text alternative of the drawing. */
@Component({
  selector: 'inv-chart-data-table',
  templateUrl: './chart-data-table.html',
  styleUrl: './chart-data-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartDataTable {
  readonly curve = input.required<readonly CurvePointResponse[]>();

  protected readonly rows = computed(() =>
    this.curve().map((point) => ({
      date: formatDate(point.date),
      portfolio: text(point.portfolio),
      msciWorld: text(point.msciWorld),
      cac40: text(point.cac40),
    })),
  );
}

function text(value: string | undefined): string {
  return value === undefined ? GAP : formatDecimal(value);
}
