import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { PeaEligibilityResponse } from '../../api/generated';
import {
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';

interface BadgeText {
  readonly label: string;
  readonly hint: string;
}

/**
 * PEA eligibility of an instrument as a short text badge (never conveyed by colour alone), followed
 * by its source as visible text. From the rule, "eligible" is only probable: the broker decides.
 */
export function eligibilityText(eligibility: PeaEligibilityResponse): BadgeText {
  const byOwner = eligibility.source === PeaEligibilityResponseSourceEnum.Owner;
  switch (eligibility.status) {
    case PeaEligibilityResponseStatusEnum.Eligible:
      return byOwner
        ? { label: 'Éligible PEA', hint: 'confirmé par vous' }
        : { label: 'PEA probable', hint: 'à vérifier chez le courtier' };
    case PeaEligibilityResponseStatusEnum.NotEligible:
      return {
        label: 'Non éligible PEA',
        hint: byOwner ? 'confirmé par vous' : 'pays hors UE/EEE, à vérifier chez le courtier',
      };
    default:
      return {
        label: 'Éligibilité PEA inconnue',
        hint: byOwner ? 'indiqué par vous' : 'à vérifier chez le courtier',
      };
  }
}

@Component({
  selector: 'inv-pea-eligibility-badge',
  templateUrl: './pea-eligibility-badge.html',
  styleUrl: './pea-eligibility-badge.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PeaEligibilityBadge {
  readonly eligibility = input.required<PeaEligibilityResponse>();

  protected readonly text = computed(() => eligibilityText(this.eligibility()));
}
