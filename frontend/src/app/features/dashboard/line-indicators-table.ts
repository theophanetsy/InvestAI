import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type {
  IndicatorResponse,
  IndicatorsResponse,
  LineIndicatorsResponse,
} from '../../api/generated';
import { LineIndicatorsResponseExclusionEnum } from '../../api/generated';
import { formatPercent } from '../../shared/format/decimal';
import type { IndicatorKind } from '../../shared/format/indicator';
import { formatIndicator } from '../../shared/format/indicator';

/** Same words as the portfolio page for a line without any close. */
const UNAVAILABLE = 'Cours indisponible';

/** One row as displayed, every text computed here under strict typing. */
interface Row {
  readonly id: string;
  readonly name: string;
  readonly codes: string;
  readonly weight: string;
  readonly cells: readonly string[];
  readonly kept: string;
}

interface Column {
  readonly label: string;
  readonly kind: IndicatorKind;
  readonly pick: (indicators: IndicatorsResponse) => IndicatorResponse;
}

/** Columns after the weight: label and how to read the indicator of the instrument. */
const COLUMNS: readonly Column[] = [
  { label: '1 mois', kind: 'signedPercent', pick: (set) => set.performanceOneMonth },
  { label: '3 mois', kind: 'signedPercent', pick: (set) => set.performanceThreeMonths },
  { label: '6 mois', kind: 'signedPercent', pick: (set) => set.performanceSixMonths },
  { label: '1 an', kind: 'signedPercent', pick: (set) => set.performanceOneYear },
  { label: 'Volatilité 1 an', kind: 'percent', pick: (set) => set.volatility },
  { label: 'Pire baisse 1 an', kind: 'signedPercent', pick: (set) => set.maxDrawdown },
  {
    label: 'Écart au MSCI World (1 an)',
    kind: 'points',
    pick: (set) => set.msciWorld.outperformance,
  },
  {
    label: 'Corrélation MSCI World',
    kind: 'coefficient',
    pick: (set) => set.msciWorld.correlation,
  },
  { label: 'Bêta MSCI World', kind: 'coefficient', pick: (set) => set.msciWorld.beta },
  {
    label: 'Écart au CAC 40 (1 an)',
    kind: 'points',
    pick: (set) => set.cac40.outperformance,
  },
  { label: 'Corrélation CAC 40', kind: 'coefficient', pick: (set) => set.cac40.correlation },
  { label: 'Bêta CAC 40', kind: 'coefficient', pick: (set) => set.cac40.beta },
  {
    label: 'Échanges quotidiens moyens (3 mois)',
    kind: 'amount',
    pick: (set) => set.averageDailyTradedAmount,
  },
];

/** Lines of the PEA with the indicators of their instrument (performance excluding dividends). */
@Component({
  selector: 'inv-line-indicators-table',
  templateUrl: './line-indicators-table.html',
  styleUrl: './line-indicators-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineIndicatorsTable {
  readonly lines = input.required<readonly LineIndicatorsResponse[]>();

  protected readonly headers = COLUMNS.map((column) => column.label);

  protected readonly rows = computed<Row[]>(() =>
    this.lines().map((line) => ({
      id: line.lineId,
      name: line.instrument.name,
      codes: `${line.instrument.ticker} · ${line.instrument.isin}`,
      weight: line.weightPercent ? formatPercent(line.weightPercent) : UNAVAILABLE,
      cells: cellsOf(line.indicators),
      kept: keptText(line),
    })),
  );
}

function cellsOf(indicators: IndicatorsResponse | undefined): string[] {
  return COLUMNS.map(({ kind, pick }) =>
    indicators ? formatIndicator(pick(indicators), kind, indicators.currency) : UNAVAILABLE,
  );
}

function keptText(line: LineIndicatorsResponse): string {
  switch (line.exclusion) {
    case LineIndicatorsResponseExclusionEnum.InsufficientHistory:
      return 'Non : historique insuffisant';
    case LineIndicatorsResponseExclusionEnum.NotValued:
      return 'Non : cours indisponible';
    default:
      return 'Oui';
  }
}
