import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { PortfolioIndicatorsResponse } from '../../api/generated';
import { LineIndicatorsResponseExclusionEnum } from '../../api/generated';
import { isInsufficient } from '../../shared/format/indicator';

/** Always shown: the indicators are computed on raw closes (spec 2.4, criterion 5). */
export const RAW_CLOSES_LIMIT =
  'Cours possiblement non ajustés : les indicateurs portent sur les clôtures brutes, hors ' +
  'dividendes et sans ajustement des divisions ni des attributions gratuites. L’année d’une ' +
  'telle opération, la performance et la pire baisse de la valeur sont faussées par une chute ' +
  'artificielle du cours.';

/** Limits and missing figures of the dashboard, as text (never conveyed by colour alone). */
@Component({
  selector: 'inv-dashboard-warnings',
  templateUrl: './dashboard-warnings.html',
  styleUrl: './dashboard-warnings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardWarnings {
  readonly indicators = input.required<PortfolioIndicatorsResponse>();

  protected readonly warnings = computed(() => {
    const { asOf, lines, volatility } = this.indicators();
    const warnings: string[] = [];
    if (!asOf) {
      warnings.push('Aucun cours enregistré : les indicateurs ne peuvent pas être calculés.');
    }
    const excluded = lines
      .filter((line) => line.exclusion !== undefined)
      .map(
        (line) =>
          `${line.instrument.name} (${
            line.exclusion === LineIndicatorsResponseExclusionEnum.NotValued
              ? 'cours indisponible'
              : 'historique insuffisant'
          })`,
      );
    if (excluded.length > 0) {
      warnings.push(
        `Exclue(s) de la volatilité, de la pire baisse, de la courbe et des corrélations : ${excluded.join(', ')}.`,
      );
    }
    if (isInsufficient(volatility)) {
      warnings.push(
        'Historique insuffisant : moins de 80 % des séances de l’année ont un cours pour le portefeuille.',
      );
    }
    warnings.push(RAW_CLOSES_LIMIT);
    return warnings;
  });
}
