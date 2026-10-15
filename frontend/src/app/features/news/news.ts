import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'inv-news',
  template: `
    <h1>Actu</h1>
    <p>Bientôt disponible (Phase 5).</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class News {}
