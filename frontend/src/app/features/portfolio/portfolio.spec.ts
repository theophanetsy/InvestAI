import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import type { PortfolioLineResponse, PortfolioResponse } from '../../api/generated';
import {
  InstrumentSummaryResponseTypeEnum,
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';
import { By } from '@angular/platform-browser';
import { provideApiClient } from '../../core/api/api-client';
import { NOTIFY_SERVER_ERRORS } from '../../core/http/http-context';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { AddLine } from './add-line';
import { CashForm } from './cash-form';
import { EligibilityDialog } from './eligibility-dialog';
import { LineDialog } from './line-dialog';
import { Portfolio } from './portfolio';

const PORTFOLIO = '/api/v1/portfolio';
const NNBSP = '\u202f';
const NBSP = '\u00a0';

const priced: PortfolioLineResponse = {
  id: 'l1',
  instrument: {
    id: 'i1',
    isin: 'FR0000120073',
    mic: 'XPAR',
    ticker: 'AI',
    name: 'Air Liquide',
    type: InstrumentSummaryResponseTypeEnum.Stock,
    currency: 'EUR',
    tracked: true,
    peaEligibility: {
      status: PeaEligibilityResponseStatusEnum.Eligible,
      source: PeaEligibilityResponseSourceEnum.Rule,
    },
  },
  quantity: 10,
  averagePrice: '150.1234',
  updatedAt: '2026-10-19T08:00:00Z',
  lastClose: { date: '2026-10-16', close: '173.1600' },
  value: '1731.60',
  unrealizedGain: '230.37',
  unrealizedGainPercent: '15.35',
  weightPercent: '86.58',
};

const unpriced: PortfolioLineResponse = {
  id: 'l2',
  instrument: {
    ...priced.instrument,
    id: 'i2',
    name: 'TotalEnergies',
    ticker: 'TTE',
    peaEligibility: {
      status: PeaEligibilityResponseStatusEnum.NotEligible,
      source: PeaEligibilityResponseSourceEnum.Owner,
    },
  },
  quantity: 5,
  updatedAt: '2026-10-19T08:00:00Z',
};

const valued: PortfolioResponse = {
  lines: [priced, unpriced],
  cash: { amount: '268.40', updatedAt: '2026-10-19T08:00:00Z', weightPercent: '13.42' },
  total: '2000.00',
  currency: 'EUR',
  complete: false,
  unpricedLines: 1,
};

describe('Portfolio', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Portfolio>;
  let element: HTMLElement;
  const dialog = { open: vi.fn() };
  const snackBar = { open: vi.fn() };

  beforeEach(() => {
    dialog.open.mockReset();
    snackBar.open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient(),
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function render(portfolio: PortfolioResponse): Promise<void> {
    fixture = TestBed.createComponent(Portfolio);
    element = fixture.nativeElement as HTMLElement;
    const load = http.expectOne(PORTFOLIO);
    // The page shows its own failure: no notification on top of it.
    expect(load.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
    load.flush(portfolio);
    await fixture.whenStable();
  }

  function cells(row: number): string[] {
    const rows = element.querySelectorAll('tr[mat-row]');
    return [...(rows[row]?.querySelectorAll('td') ?? [])].map((cell) =>
      cell.textContent.replace(/[ \n]+/g, ' ').trim(),
    );
  }

  function button(label: string): HTMLButtonElement {
    const found = element.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found;
  }

  it('shows the valued lines formatted in French, as sent by the backend', async () => {
    await render(valued);

    expect(element.querySelector('[data-testid="portfolio-total"]')?.textContent).toBe(
      `2${NNBSP}000,00${NBSP}€`,
    );
    expect(cells(0)).toEqual([
      'Air LiquideAI · FR0000120073PEA probable (à vérifier chez le courtier)',
      '10',
      `173,16${NBSP}€ au 16/10/2026`,
      `1${NNBSP}731,60${NBSP}€`,
      `86,58${NBSP}%`,
      `+230,37${NBSP}€ (+15,35${NBSP}%)`,
      'Modifier Éligibilité Supprimer',
    ]);
    expect(cells(1).slice(2, 6)).toEqual(['Cours indisponible', '—', '—', '—']);
    expect(cells(1)[0]).toContain('Non éligible PEA');
    expect(element.querySelector('[data-testid="portfolio-incomplete"]')?.textContent).toContain(
      '1 ligne(s) sans cours connu',
    );
    expect(element.textContent).toContain(`Part des espèces : 13,42${NBSP}%`);
    // Regression (real-app verification of phase 2.3): the note was glued to the total.
    const total = element.querySelector('.summary__total')?.textContent ?? '';
    expect(total).toContain('(espèces comprises)');
    expect(total).not.toContain('€(');
    expect(element.querySelector('caption')?.textContent.trim()).toBe('Lignes du PEA');
  });

  it('invites to add a first line when the portfolio is empty', async () => {
    await render({ lines: [], total: '0.00', currency: 'EUR', complete: true, unpricedLines: 0 });

    expect(element.querySelector('[data-testid="portfolio-empty"]')).not.toBeNull();
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('[data-testid="portfolio-incomplete"]')).toBeNull();
    expect(element.querySelector('inv-add-line')).not.toBeNull();
  });

  it('deletes a line once confirmed, then values the portfolio again', async () => {
    await render(valued);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    button('Supprimer Air Liquide').click();

    expect(dialog.open).toHaveBeenCalledWith(ConfirmDialog, expect.anything());
    const remove = http.expectOne(`${PORTFOLIO}/lines/l1`);
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(PORTFOLIO).flush({ ...valued, lines: [unpriced] });
    await fixture.whenStable();
    expect(snackBar.open).toHaveBeenCalledWith('Ligne supprimée.', 'Fermer', { duration: 4000 });
    expect(element.querySelectorAll('tr[mat-row]')).toHaveLength(1);
    expect(document.activeElement).toBe(element.querySelector('h1'));
  });

  it('keeps the line when the deletion is cancelled or fails', async () => {
    await render(valued);
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    button('Supprimer Air Liquide').click();
    http.expectNone(`${PORTFOLIO}/lines/l1`);

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    button('Supprimer Air Liquide').click();
    http
      .expectOne(`${PORTFOLIO}/lines/l1`)
      .flush(
        { status: 404, title: 'Error', code: 'portfolio.line.not-found', requestId: 'r4' },
        { status: 404, statusText: 'Not Found' },
      );
    expect(snackBar.open).toHaveBeenCalledWith('La suppression a échoué. Référence : r4', 'Fermer');
    // Already gone (deleted elsewhere): the table is refreshed.
    http.expectOne(PORTFOLIO).flush({ ...valued, lines: [unpriced] });
  });

  it('announces a line added and the cash saved below the table, then values again', async () => {
    await render(valued);

    fixture.debugElement.query(By.directive(AddLine)).injector.get(AddLine).added.emit();
    http.expectOne(PORTFOLIO).flush(valued);
    fixture.debugElement.query(By.directive(CashForm)).injector.get(CashForm).saved.emit();
    http.expectOne(PORTFOLIO).flush(valued);

    expect(snackBar.open).toHaveBeenCalledWith('Ligne ajoutée.', 'Fermer', { duration: 4000 });
    expect(snackBar.open).toHaveBeenCalledWith('Espèces enregistrées.', 'Fermer', {
      duration: 4000,
    });
  });

  it('opens the edit and eligibility dialogs, and reloads once they saved', async () => {
    await render(valued);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    button('Modifier Air Liquide').click();
    expect(dialog.open).toHaveBeenCalledWith(LineDialog, { data: priced });
    http.expectOne(PORTFOLIO).flush(valued);

    button('Corriger l’éligibilité PEA de TotalEnergies').click();
    expect(dialog.open).toHaveBeenCalledWith(EligibilityDialog, { data: unpriced.instrument });
    http.expectOne(PORTFOLIO).flush(valued);

    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    button('Modifier Air Liquide').click();
    http.expectNone(PORTFOLIO);
  });

  it('offers to retry when the portfolio cannot be loaded', async () => {
    fixture = TestBed.createComponent(Portfolio);
    element = fixture.nativeElement as HTMLElement;
    http.expectOne(PORTFOLIO).error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"] p')?.textContent.trim()).toBe(
      'Serveur injoignable. Vérifiez votre connexion et réessayez.',
    );
    element.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    http.expectOne(PORTFOLIO).flush(valued);
    await fixture.whenStable();
    expect(element.querySelectorAll('tr[mat-row]')).toHaveLength(2);
  });
});
