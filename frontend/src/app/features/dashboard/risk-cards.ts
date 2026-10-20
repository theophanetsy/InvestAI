import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { PortfolioIndicatorsResponse } from '../../api/generated';
import { formatAmount, formatDate } from '../../shared/format/decimal';
import { formatIndicator } from '../../shared/format/indicator';

interface Card {
  readonly title: string;
  readonly note: string;
  readonly items: readonly {
    readonly id: string;
    readonly label: string;
    readonly value: string;
  }[];
}

/** Total value, risk and concentration cards: figures of the backend, only formatted here. */
@Component({
  selector: 'inv-risk-cards',
  templateUrl: './risk-cards.html',
  styleUrl: './risk-cards.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskCards {
  readonly indicators = input.required<PortfolioIndicatorsResponse>();

  protected readonly cards = computed<Card[]>(() => {
    const { asOf, total, currency, volatility, maxDrawdown, composition } = this.indicators();
    return [
      {
        title: 'Valeur totale',
        note: asOf ? `Cours au ${formatDate(asOf)}` : 'Aucun cours connu',
        items: [{ id: 'total', label: 'Espèces comprises', value: formatAmount(total, currency) }],
      },
      {
        title: 'Risque sur un an',
        note: 'Portefeuille actuel comme s’il avait été détenu depuis un an, espèces exclues',
        items: [
          { id: 'volatility', label: 'Volatilité', value: formatIndicator(volatility, 'percent') },
          {
            id: 'max-drawdown',
            label: 'Pire baisse',
            value: formatIndicator(maxDrawdown, 'signedPercent'),
          },
        ],
      },
      {
        title: 'Concentration',
        note: 'Herfindahl des lignes investies, hors espèces : 1 pour une seule ligne, 1/n pour n lignes égales',
        items: [
          { id: 'line-count', label: 'Lignes', value: String(composition.lineCount) },
          {
            id: 'largest-line',
            label: 'Plus grosse ligne',
            value: formatIndicator(composition.largestLinePercent, 'percent'),
          },
          {
            id: 'herfindahl',
            label: 'Herfindahl',
            value: formatIndicator(composition.herfindahlIndex, 'coefficient'),
          },
        ],
      },
      {
        title: 'Répartition',
        note: 'Part de la valeur totale',
        items: [
          {
            id: 'stocks',
            label: 'Actions',
            value: formatIndicator(composition.stockPercent, 'percent'),
          },
          { id: 'etfs', label: 'ETF', value: formatIndicator(composition.etfPercent, 'percent') },
          {
            id: 'others',
            label: 'Autres',
            value: formatIndicator(composition.otherPercent, 'percent'),
          },
          {
            id: 'cash',
            label: 'Espèces',
            value: formatIndicator(composition.cashPercent, 'percent'),
          },
        ],
      },
    ];
  });
}
