import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'inv-chat',
  template: `
    <h1>Assistant</h1>
    <p>Bientôt disponible (Phase 3).</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Chat {}
