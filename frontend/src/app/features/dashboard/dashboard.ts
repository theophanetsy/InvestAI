import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'inv-dashboard',
  template: `
    <h1>Tableau de bord</h1>
    <p>Bientôt disponible (Phase 2).</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {}
