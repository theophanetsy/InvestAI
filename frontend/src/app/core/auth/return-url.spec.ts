import { HOME_URL, safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it.each(['/simulation', '/actu?date=2026-01-02', '/dashboard#positions'])(
    'keeps the internal path %s',
    (url) => {
      expect(safeReturnUrl(url)).toBe(url);
    },
  );

  it.each([
    undefined,
    '',
    'simulation',
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
    '/\t/evil.example',
    '/\n/evil.example',
    '/simulation\\..',
  ])('falls back to the home page for %s', (url) => {
    expect(safeReturnUrl(url)).toBe(HOME_URL);
  });
});
