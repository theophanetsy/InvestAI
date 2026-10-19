import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDialog } from './confirm-dialog';

describe('ConfirmDialog', () => {
  const close = vi.fn();

  beforeEach(() => {
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { title: 'Supprimer ?', message: 'Air Liquide', confirmLabel: 'Supprimer' },
        },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(element: HTMLElement, label: string): HTMLButtonElement {
    const found = [...element.querySelectorAll('button')].find(
      (candidate) => candidate.textContent.trim() === label,
    );
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found;
  }

  it('asks the question with its title', async () => {
    const element = await render();

    expect(element.querySelector('h2')?.textContent.trim()).toBe('Supprimer ?');
    expect(element.querySelector('p')?.textContent.trim()).toBe('Air Liquide');
  });

  it('closes with true only when confirmed', async () => {
    const element = await render();

    button(element, 'Annuler').click();
    button(element, 'Supprimer').click();

    expect(close.mock.calls).toEqual([[false], [true]]);
  });
});
