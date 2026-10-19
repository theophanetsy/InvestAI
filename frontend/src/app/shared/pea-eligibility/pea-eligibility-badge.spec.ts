import { TestBed } from '@angular/core/testing';
import {
  PeaEligibilityResponseSourceEnum as Source,
  PeaEligibilityResponseStatusEnum as Status,
} from '../../api/generated';
import { PeaEligibilityBadge, eligibilityText } from './pea-eligibility-badge';

describe('PeaEligibilityBadge', () => {
  it('says that an eligibility from the rule is only probable', () => {
    expect(eligibilityText({ status: Status.Eligible, source: Source.Rule })).toEqual({
      label: 'PEA probable',
      hint: 'Probable : vérifier chez le courtier',
    });
    expect(eligibilityText({ status: Status.NotEligible, source: Source.Rule }).hint).toBe(
      'Pays hors UE/EEE : vérifier chez le courtier',
    );
    expect(eligibilityText({ status: Status.Unknown, source: Source.Rule }).label).toBe(
      'Éligibilité PEA inconnue',
    );
  });

  it('says when the owner decided', () => {
    expect(eligibilityText({ status: Status.Eligible, source: Source.Owner }).label).toBe(
      'Éligible PEA',
    );
    expect(eligibilityText({ status: Status.NotEligible, source: Source.Owner }).hint).toBe(
      'Confirmé par vous',
    );
    expect(eligibilityText({ status: Status.Unknown, source: Source.Owner }).hint).toBe(
      'Indiqué par vous',
    );
  });

  it('renders the label with its hint as text, not as a colour only', async () => {
    const fixture = TestBed.createComponent(PeaEligibilityBadge);
    fixture.componentRef.setInput('eligibility', { status: Status.Eligible, source: Source.Rule });
    await fixture.whenStable();
    const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge');

    expect(badge?.textContent.replace(/\s+/g, ' ').trim()).toBe(
      'PEA probable (Probable : vérifier chez le courtier)',
    );
    expect(badge?.getAttribute('data-status')).toBe('ELIGIBLE');
  });
});
