import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { IndicatorResponse, LineIndicatorsResponse } from '../../api/generated';
import { LineIndicatorsResponseExclusionEnum } from '../../api/generated';
import { formatPercent } from '../../shared/format/decimal';
import type { IndicatorKind } from '../../shared/format/indicator';
import { formatIndicator } from '../../shared/format/indicator';

const NONE = '—';

/** One row as displayed, every text computed here under strict typing. */
interface Row {
  readonly id: string;
  readonly name: string;
  readonly codes: string;
  readonly weight: string;
  readonly cells: readonly string[];
  readonly kept: string;
}

/** Columns after the weight: label and how to read the indicator of the line. */
const COLUMNS: readonly {
  label: string;
  kind: IndicatorKind;
  pick: (line: LineIndicatorsResponse) => IndicatorResponse | undefined;
}[] = [
  { label: '1 mois', kind: 'signedPercent', pick: (line) => line.indicators?.performanceOneMonth },
  {
    label: '3 mois',
    kind: 'signedPercent',
    pick: (line) => line.indicators?.performanceThreeMonths,
  },
  { label: '6 mois', kind: 'signedPercent', pick: (line) => line.indicators?.performanceSixMonths },
  { label: '1 an', kind: 'signedPercent', pick: (line) => line.indicators?.performanceOneYear },
  { label: 'Volatilité 1 an', kind: 'percent', pick: (line) => line.indicators?.volatility },
  {
    label: 'Pire baisse 1 an',
    kind: 'signedPercent',
    pick: (line) => line.indicators?.maxDrawdown,
  },
  {
    label: 'Bêta MSCI World',
    kind: 'coefficient',
    pick: (line) => line.indicators?.msciWorld.beta,
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
      weight: line.weightPercent ? formatPercent(line.weightPercent) : NONE,
      cells: COLUMNS.map(({ kind, pick }) => {
        const indicator = pick(line);
        return indicator ? formatIndicator(indicator, kind) : 'Aucun cours';
      }),
      kept: keptText(line),
    })),
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
