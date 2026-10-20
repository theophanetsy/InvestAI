import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiClient } from '../../core/api/api-client';
import { CashForm } from './cash-form';

const CASH = '/api/v1/portfolio/cash';

describe('CashForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CashForm>;
  let element: HTMLElement;
  let saved: number;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CashForm);
    element = fixture.nativeElement as HTMLElement;
    saved = 0;
    fixture.componentInstance.saved.subscribe(() => {
      saved += 1;
    });
  });

  afterEach(() => {
    http.verify();
  });

  function amount(): HTMLInputElement {
    const input = element.querySelector<HTMLInputElement>('input');
    if (!input) {
      throw new Error('No input');
    }
    return input;
  }

  async function submit(value: string): Promise<void> {
    amount().value = value;
    amount().dispatchEvent(new Event('input'));
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('shows the declared cash in French and saves a new amount as a decimal string', async () => {
    fixture.componentRef.setInput('cash', { amount: '268.40', updatedAt: '2026-10-19T08:00:00Z' });
    await fixture.whenStable();
    expect(amount().value).toBe('268,40');

    await submit('1 000,5');

    const save = http.expectOne(CASH);
    expect(save.request.body).toEqual({ amount: '1000.5' });
    save.flush({ amount: '1000.50', updatedAt: '2026-10-19T08:00:00Z' });
    expect(saved).toBe(1);
  });

  it('keeps an amount being typed when the portfolio is reloaded', async () => {
    fixture.componentRef.setInput('cash', { amount: '1.00', updatedAt: '2026-10-19T08:00:00Z' });
    await fixture.whenStable();
    amount().value = '5';
    amount().dispatchEvent(new Event('input'));

    fixture.componentRef.setInput('cash', { amount: '1.00', updatedAt: '2026-10-19T09:00:00Z' });
    await fixture.whenStable();

    expect(amount().value).toBe('5');
  });

  it('refuses a negative amount and explains a server failure', async () => {
    await fixture.whenStable();

    await submit('-5');
    http.expectNone(CASH);
    expect(element.querySelector('mat-error')?.textContent.trim()).toBe(
      'Montant invalide : zéro ou plus, deux décimales au plus.',
    );
    expect(element.querySelector('[role="alert"]')).toBeNull();

    await submit('0');
    http
      .expectOne(CASH)
      .flush(
        { status: 500, title: 'Error', code: 'internal.error', requestId: 'r3' },
        { status: 500, statusText: 'Error' },
      );
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent.trim()).toBe(
      'L’enregistrement des espèces a échoué. Référence : r3',
    );
    expect(saved).toBe(0);
  });
});
