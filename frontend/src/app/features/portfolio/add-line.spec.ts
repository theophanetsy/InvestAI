import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import type { InstrumentSummaryResponse } from '../../api/generated';
import {
  InstrumentSummaryResponseTypeEnum,
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';
import { provideApiClient } from '../../core/api/api-client';
import { NOTIFY_SERVER_ERRORS } from '../../core/http/http-context';
import { AddLine } from './add-line';

const airLiquide: InstrumentSummaryResponse = {
  id: '7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c',
  isin: 'FR0000120073',
  mic: 'XPAR',
  ticker: 'AI',
  name: 'Air Liquide',
  type: InstrumentSummaryResponseTypeEnum.Stock,
  currency: 'EUR',
  tracked: false,
  peaEligibility: {
    status: PeaEligibilityResponseStatusEnum.Eligible,
    source: PeaEligibilityResponseSourceEnum.Rule,
  },
};

const LINES = '/api/v1/portfolio/lines';

describe('AddLine', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AddLine>;
  let element: HTMLElement;
  let added: number;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddLine);
    element = fixture.nativeElement as HTMLElement;
    added = 0;
    fixture.componentInstance.added.subscribe(() => {
      added += 1;
    });
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
  });

  function input(selector: string): HTMLInputElement {
    const found = element.querySelector<HTMLInputElement>(selector);
    if (!found) {
      throw new Error(`No input ${selector}`);
    }
    return found;
  }

  async function type(selector: string, value: string): Promise<void> {
    const field = input(selector);
    field.focus();
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  /** Types a query and answers the search once the debounce has elapsed. */
  async function search(query: string, results: InstrumentSummaryResponse[]): Promise<void> {
    await type('input[type="search"]', query);
    const request = await vi.waitFor(() =>
      http.expectOne((candidate) => candidate.url === '/api/v1/market/instruments'),
    );
    expect(request.request.params.get('query')).toBe(query);
    request.flush(results);
    await fixture.whenStable();
  }

  async function chooseFirstOption(): Promise<void> {
    const option = document.querySelector<HTMLElement>('mat-option');
    if (!option) {
      throw new Error('No option');
    }
    option.click();
    await fixture.whenStable();
  }

  function submit(): void {
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
  }

  function alertText(): string | undefined {
    return element.querySelector('[role="alert"]')?.textContent.trim();
  }

  it('searches by name, shows the eligibility, then adds the chosen line', async () => {
    await search('air', [airLiquide]);
    expect(document.querySelector('mat-option')?.textContent).toContain('PEA probable');
    await chooseFirstOption();
    expect(element.querySelector('[data-testid="selected-instrument"]')?.textContent).toContain(
      'Air Liquide (AI, FR0000120073)',
    );

    await type('input[formcontrolname="quantity"]', '10');
    await type('input[formcontrolname="averagePrice"]', '150,1234');
    submit();

    const add = http.expectOne(LINES);
    expect(add.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
    expect(add.request.body).toEqual({
      instrumentId: airLiquide.id,
      quantity: 10,
      averagePrice: '150.1234',
    });
    add.flush({ id: 'l1', instrumentId: airLiquide.id, quantity: 10, updatedAt: '' });
    await fixture.whenStable();
    expect(added).toBe(1);
    expect(element.querySelector('[data-testid="selected-instrument"]')).toBeNull();
    // A fresh form: no error shown on the emptied, required quantity.
    expect(element.querySelector('mat-error')).toBeNull();
    expect(input('input[formcontrolname="quantity"]').getAttribute('aria-invalid')).not.toBe(
      'true',
    );
  });

  it('does not search below two characters and says when nothing is found', async () => {
    await type('input[type="search"]', 'a');
    await new Promise((resolve) => setTimeout(resolve, 350));
    http.expectNone((candidate) => candidate.url === '/api/v1/market/instruments');

    await search('zzz', []);
    expect(element.textContent).toContain('Aucune valeur trouvée.');
  });

  it('says when the search fails, without a global notification', async () => {
    await type('input[type="search"]', 'air');
    const request = await vi.waitFor(() =>
      http.expectOne((candidate) => candidate.url === '/api/v1/market/instruments'),
    );
    expect(request.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
    request.flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(element.textContent).toContain('Recherche impossible pour le moment.');
  });

  it('asks for an instrument and valid figures before calling the backend', async () => {
    submit();
    await fixture.whenStable();
    expect(alertText()).toBe('Choisissez d’abord une valeur dans la liste.');

    await search('air', [airLiquide]);
    await chooseFirstOption();
    await type('input[formcontrolname="quantity"]', '1.5');
    submit();
    await fixture.whenStable();

    http.expectNone(LINES);
    expect(element.querySelector('mat-error')?.textContent.trim()).toBe(
      'Indiquez un nombre entier de titres, au moins 1.',
    );
  });

  it.each([
    ['portfolio.line.duplicate-instrument', 'Cette valeur est déjà dans le portefeuille'],
    ['portfolio.line.currency-not-supported', 'Seules les valeurs cotées en euros'],
    ['instrument.not-found', 'Cette valeur n’existe plus dans le référentiel.'],
    ['internal.error', 'L’ajout de la ligne a échoué. Référence : r1'],
  ])('explains a %s refusal', async (code, message) => {
    await search('air', [airLiquide]);
    await chooseFirstOption();
    await type('input[formcontrolname="quantity"]', '1');
    submit();

    http
      .expectOne(LINES)
      .flush(
        { status: 422, title: 'Error', code, requestId: 'r1' },
        { status: 422, statusText: '' },
      );
    await fixture.whenStable();

    expect(alertText()).toContain(message);
    expect(added).toBe(0);
  });
});
