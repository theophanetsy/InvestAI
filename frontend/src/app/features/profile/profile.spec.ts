import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import type { ProfileResponse, RiskProfileResponse } from '../../api/generated';
import { ProfileResponseRiskProfileEnum, RiskProfileResponseCodeEnum } from '../../api/generated';
import { provideApiClient } from '../../core/api/api-client';
import { Profile } from './profile';
import { toRequestRiskProfile } from './risk-profile';

const PROFILE = '/api/v1/profile';
const RISK_PROFILES = '/api/v1/profile/risk-profiles';

const riskProfiles: RiskProfileResponse[] = [
  { code: RiskProfileResponseCodeEnum.Prudent, label: 'Prudent', description: 'Environ 10 %.' },
  { code: RiskProfileResponseCodeEnum.Balanced, label: 'Équilibré', description: 'Environ 20 %.' },
];

const stored: ProfileResponse = {
  amountToInvest: '20000.50',
  currency: 'EUR',
  horizonYears: 10,
  riskProfile: ProfileResponseRiskProfileEnum.Balanced,
  exclusions: ['Tabac'],
  objectives: '<img src=x onerror=alert(1)>',
  peaOpenedOn: '2019-03-01',
  updatedAt: '2026-10-19T08:00:00Z',
};

function problem(status: number, code: string): object {
  return { status, title: 'Error', code, requestId: 'r1' };
}

describe('Profile', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Profile>;
  let element: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function render(profile: ProfileResponse | null): Promise<void> {
    fixture = TestBed.createComponent(Profile);
    element = fixture.nativeElement as HTMLElement;
    http.expectOne(RISK_PROFILES).flush(riskProfiles);
    const get = http.expectOne((request) => request.method === 'GET' && request.url === PROFILE);
    if (profile) {
      get.flush(profile);
    } else {
      get.flush(problem(404, 'profile.not-found'), { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
  }

  function field(label: string): HTMLInputElement | HTMLTextAreaElement {
    const labelElement = [...element.querySelectorAll('label')].find((candidate) =>
      candidate.textContent.trim().startsWith(label),
    );
    const input =
      labelElement &&
      element.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${labelElement.htmlFor}`);
    if (!input) {
      throw new Error(`No field labelled ${label}`);
    }
    return input;
  }

  function type(label: string, value: string): void {
    const input = field(label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function radio(label: string): HTMLInputElement {
    const input = [...element.querySelectorAll<HTMLInputElement>('input[type="radio"]')].find(
      (candidate) => candidate.labels?.[0]?.textContent.trim() === label,
    );
    if (!input) {
      throw new Error(`No radio ${label}`);
    }
    return input;
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  function text(selector: string): string | undefined {
    return element.querySelector(selector)?.textContent.trim();
  }

  it('starts empty when no profile is saved, then saves the typed values', async () => {
    await render(null);
    expect(text('[data-testid="profile-state"]')).toContain('Aucun profil enregistré');

    type('Somme à placer', ' 20 000,5 ');
    type('Horizon de placement', '10');
    radio('Équilibré').click();
    await fixture.whenStable();
    expect(text('[data-testid="risk-description"]')).toBe('Environ 20 %.');
    await submit();

    const save = http.expectOne((request) => request.method === 'PUT' && request.url === PROFILE);
    expect(save.request.body).toEqual({
      amountToInvest: '20000.5',
      horizonYears: 10,
      riskProfile: 'BALANCED',
      exclusions: [],
    });
    save.flush({ ...stored, objectives: undefined });
    await fixture.whenStable();
    expect(text('[role="status"]')).toBe('Profil enregistré.');
  });

  it('fills the form with the saved profile and shows untrusted text as text', async () => {
    await render(stored);

    expect(field('Somme à placer').value).toBe('20000,50');
    expect(field('Vos objectifs').value).toBe('<img src=x onerror=alert(1)>');
    expect(element.querySelector('img')).toBeNull();
    expect(text('mat-chip-row')).toContain('Tabac');
    expect(text('[data-testid="risk-description"]')).toBe('Environ 20 %.');
  });

  it('adds and removes exclusions, then sends the objectives and the opening date', async () => {
    await render(stored);
    const input = element.querySelector<HTMLInputElement>('input[maxlength="100"]');
    if (!input) {
      throw new Error('No exclusion input');
    }

    input.value = 'Armement';
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13 }));
    input.value = 'Armement';
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13 }));
    await fixture.whenStable();
    element.querySelector<HTMLButtonElement>('[aria-label="Retirer Tabac"]')?.click();
    await fixture.whenStable();
    await submit();

    const save = http.expectOne((request) => request.method === 'PUT');
    expect(save.request.body).toMatchObject({
      exclusions: ['Armement'],
      objectives: '<img src=x onerror=alert(1)>',
      peaOpenedOn: '2019-03-01',
    });
    save.flush(stored);
  });

  it('refuses invalid values before calling the server', async () => {
    await render(null);

    type('Somme à placer', '0');
    type('Date d’ouverture du PEA', '2999-01-01');
    await submit();

    http.expectNone((request) => request.method === 'PUT');
    expect(text('mat-error')).toBe('La somme doit être supérieure à zéro.');
    expect(element.textContent).toContain('Choisissez un profil de risque.');
    expect(element.textContent).toContain('La date ne peut pas être dans le futur.');
  });

  it('says when the server refuses or fails', async () => {
    await render(stored);

    await submit();
    http
      .expectOne((request) => request.method === 'PUT')
      .flush(problem(400, 'validation.failed'), { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(text('[role="alert"]')).toBe(
      'Certaines valeurs ont été refusées par le serveur : vérifiez le formulaire.',
    );

    await submit();
    http
      .expectOne((request) => request.method === 'PUT')
      .flush(problem(500, 'internal.error'), { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(text('[role="alert"]')).toBe('L’enregistrement du profil a échoué. Référence : r1');
  });

  it('offers to retry when the profile cannot be loaded', async () => {
    fixture = TestBed.createComponent(Profile);
    element = fixture.nativeElement as HTMLElement;
    http.expectOne(RISK_PROFILES).error(new ProgressEvent('error'));
    http.expectOne(PROFILE);
    await fixture.whenStable();

    expect(text('[role="alert"] p')).toBe(
      'Serveur injoignable. Vérifiez votre connexion et réessayez.',
    );
    element.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    http.expectOne(RISK_PROFILES).flush(riskProfiles);
    http.expectOne(PROFILE).flush(stored);
    await fixture.whenStable();
    expect(field('Horizon de placement').value).toBe('10');
  });

  it('converts the risk profile code back to the request enum', () => {
    expect(toRequestRiskProfile('DYNAMIC')).toBe('DYNAMIC');
    expect(toRequestRiskProfile('YOLO')).toBeNull();
  });
});
