import { TestBed } from '@angular/core/testing';
import { Assistant } from './assistant';

describe('Assistant', () => {
  it('sends the owner to Claude, the application having no chat', () => {
    const fixture = TestBed.createComponent(Assistant);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toBe('Assistant');
    expect(element.textContent).toContain('Claude Desktop ou Claude Code');
    expect(element.textContent).toContain('pas de chat');
    expect(element.textContent).toContain('serveur MCP');
    expect(element.textContent).not.toContain('Bientôt disponible');
  });
});
