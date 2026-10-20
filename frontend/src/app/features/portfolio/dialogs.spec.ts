import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { Type } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import type { InstrumentSummaryResponse, PortfolioLineResponse } from '../../api/generated';
import {
  InstrumentSummaryResponseTypeEnum,
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';
import { provideApiClient } from '../../core/api/api-client';
import { EligibilityDialog } from './eligibility-dialog';
import { LineDialog } from './line-dialog';

const instrument: InstrumentSummaryResponse = {
  id: 'i1',
  isin: 'FR0010315770',
  mic: 'XPAR',
  ticker: 'ESE',
  name: 'BNP S&P 500',
  type: InstrumentSummaryResponseTypeEnum.Etf,
  currency: 'EUR',
  tracked: true,
  peaEligibility: {
    status: PeaEligibilityResponseStatusEnum.Unknown,
    source: PeaEligibilityResponseSourceEnum.Rule,
  },
};

const line: PortfolioLineResponse = {
  id: 'l1',
  instrument,
  quantity: 10,
  averagePrice: '150.1234',
  updatedAt: '2026-10-19T08:00:00Z',
};

describe('portfolio dialogs', () => {
  let http: HttpTestingController;
  const close = vi.fn();

  function open<T>(component: Type<T>, data: unknown): ComponentFixture<T> {
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close, disableClose: false } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(component);
  }

  afterEach(() => {
    http.verify();
  });

  function button(element: HTMLElement, label: string): HTMLButtonElement {
    const found = [...element.querySelectorAll('button')].find(
      (candidate) => candidate.textContent.trim() === label,
    );
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found;
  }

  describe('LineDialog', () => {
    it('starts from the declared values and saves the new ones', async () => {
      const fixture = open(LineDialog, line);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;
      const price = element.querySelector<HTMLInputElement>('[formcontrolname="averagePrice"]');
      expect(price?.value).toBe('150,1234');

      if (price) {
        price.value = '';
        price.dispatchEvent(new Event('input'));
      }
      button(element, 'Enregistrer').click();

      const update = http.expectOne('/api/v1/portfolio/lines/l1');
      expect(fixture.debugElement.injector.get(MatDialogRef).disableClose).toBe(true);
      expect(update.request.method).toBe('PUT');
      expect(update.request.body).toEqual({ quantity: 10 });
      update.flush({ id: 'l1', instrumentId: 'i1', quantity: 10, updatedAt: '' });
      expect(close).toHaveBeenCalledWith(true);
    });

    it('stays open and explains a failure', async () => {
      const fixture = open(LineDialog, line);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      button(element, 'Enregistrer').click();
      http
        .expectOne('/api/v1/portfolio/lines/l1')
        .flush(
          { status: 404, title: 'Not Found', code: 'portfolio.line.not-found', requestId: 'r2' },
          { status: 404, statusText: 'Not Found' },
        );
      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')?.textContent.trim()).toBe(
        'La modification a échoué. Référence : r2',
      );
      expect(close).not.toHaveBeenCalled();
      expect(fixture.debugElement.injector.get(MatDialogRef).disableClose).toBe(false);

      button(element, 'Enregistrer').click();
      await fixture.whenStable();
      expect(element.querySelector('[role="alert"]')).toBeNull();
      http.expectOne('/api/v1/portfolio/lines/l1').flush({});
    });

    it('refuses an invalid price', async () => {
      const fixture = open(LineDialog, line);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;
      const price = element.querySelector<HTMLInputElement>('[formcontrolname="averagePrice"]');
      if (price) {
        price.value = '-3';
        price.dispatchEvent(new Event('input'));
      }

      button(element, 'Enregistrer').click();

      http.expectNone('/api/v1/portfolio/lines/l1');
    });
  });

  describe('EligibilityDialog', () => {
    it('preselects the previous answer of the owner, not the guess of the rule', async () => {
      const corrected = {
        ...instrument,
        peaEligibility: {
          status: PeaEligibilityResponseStatusEnum.NotEligible,
          source: PeaEligibilityResponseSourceEnum.Owner,
        },
      };
      const fixture = open(EligibilityDialog, corrected);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      expect(
        element.querySelector<HTMLInputElement>('input[type="radio"][value="NOT_ELIGIBLE"]')
          ?.checked,
      ).toBe(true);
    });

    it('stores the owner answer', async () => {
      const fixture = open(EligibilityDialog, instrument);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;
      expect(element.textContent).toContain('Éligibilité PEA inconnue');

      element.querySelector<HTMLInputElement>('input[type="radio"][value="ELIGIBLE"]')?.click();
      button(element, 'Enregistrer').click();

      const correct = http.expectOne('/api/v1/market/instruments/i1/pea-eligibility');
      expect(correct.request.body).toEqual({ status: 'ELIGIBLE' });
      correct.flush({ status: 'ELIGIBLE', source: 'OWNER' });
      expect(close).toHaveBeenCalledWith(true);
    });

    it('asks for an answer, then explains a failure', async () => {
      const fixture = open(EligibilityDialog, instrument);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      button(element, 'Enregistrer').click();
      await fixture.whenStable();
      expect(element.querySelector('[role="alert"]')?.textContent.trim()).toBe(
        'Choisissez une réponse.',
      );

      element.querySelector<HTMLInputElement>('input[type="radio"][value="UNKNOWN"]')?.click();
      button(element, 'Enregistrer').click();
      http
        .expectOne('/api/v1/market/instruments/i1/pea-eligibility')
        .error(new ProgressEvent('error'));
      await fixture.whenStable();
      expect(element.querySelector('[role="alert"]')?.textContent.trim()).toBe(
        'Serveur injoignable. Vérifiez votre connexion et réessayez.',
      );
    });
  });
});
