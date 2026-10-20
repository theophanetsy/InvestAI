import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { CurvePointResponse } from '../../api/generated';
import { formatDate, formatDecimal } from '../../shared/format/decimal';
import type { SeriesValues } from './chart-geometry';
import {
  FRAME,
  anchorOf,
  pathOf,
  scaleOf,
  tickLabel,
  ticksOf,
  toNumbers,
  xOf,
  yOf,
} from './chart-geometry';
import { ChartDataTable } from './chart-data-table';

type SeriesKey = 'portfolio' | 'msciWorld' | 'cac40';

/** The three curves, told apart by their line pattern as well as by their colour. */
const SERIES: readonly { key: SeriesKey; label: string; style: string }[] = [
  { key: 'portfolio', label: 'Portefeuille actuel', style: 'chart__line chart__line--portfolio' },
  { key: 'msciWorld', label: 'ETF MSCI World (CW8)', style: 'chart__line chart__line--msci-world' },
  { key: 'cac40', label: 'ETF CAC 40 (CAC)', style: 'chart__line chart__line--cac-40' },
];

interface DrawnSeries {
  readonly key: SeriesKey;
  readonly label: string;
  /** CSS classes: colour and line pattern. */
  readonly style: string;
  /** Last known value, as sent by the backend and formatted, or "non disponible". */
  readonly last: string;
  /** Empty when the series has no value at all (benchmark without enough history). */
  readonly path: string;
}

let nextId = 0;

/**
 * The current portfolio as if held for a year against the two benchmark ETFs, base 100 (spec 2.4,
 * criterion 10). Plain SVG, no chart library: the drawing has a title and a description for
 * screen readers, and the values are also given as a table.
 */
@Component({
  selector: 'inv-performance-chart',
  imports: [ChartDataTable],
  templateUrl: './performance-chart.html',
  styleUrl: './performance-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerformanceChart {
  /** One point per session, at least one. */
  readonly curve = input.required<readonly CurvePointResponse[]>();

  protected readonly frame = FRAME;
  protected readonly descriptionId = `performance-chart-description-${String(nextId++)}`;

  private readonly values = computed(() =>
    SERIES.map(({ key }) => this.curve().map((point) => point[key])),
  );
  private readonly scale = computed(() => scaleOf(this.values().map(toNumbers)));

  /** Every series, in the legend and the description even when it has no value. */
  protected readonly series = computed<DrawnSeries[]>(() =>
    SERIES.map(({ key, label, style }, index) => {
      const values = this.values()[index] ?? [];
      return {
        key,
        label,
        style,
        last: lastText(values),
        path: pathOf(toNumbers(values), this.scale()),
      };
    }),
  );

  protected readonly drawn = computed(() => this.series().filter((line) => line.path !== ''));

  protected readonly ticks = computed(() =>
    ticksOf(this.scale()).map((tick) => ({
      y: yOf(tick, this.scale()),
      label: tickLabel(tick),
      base: tick === 100,
    })),
  );

  protected readonly dates = computed(() => {
    const points = this.curve();
    const indexes = [...new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])];
    return indexes.map((index) => ({
      x: xOf(index, points.length),
      label: formatDate(points[index]?.date ?? ''),
      anchor: anchorOf(index, points.length),
    }));
  });

  protected readonly description = computed(() => {
    const points = this.curve();
    const start = formatDate(points[0]?.date ?? '');
    const end = formatDate(points[points.length - 1]?.date ?? '');
    const finals = this.series().map(({ label, last }) => `${label} ${last}`);
    return `Base 100 le ${start}. Dernières valeurs au ${end} : ${finals.join(' ; ')}.`;
  });
}

function lastText(values: SeriesValues): string {
  // No Array.findLast: lib ES2023 is not enabled (tsconfig).
  const last = [...values].reverse().find((value) => value !== undefined);
  return last === undefined ? 'non disponible' : formatDecimal(last);
}
