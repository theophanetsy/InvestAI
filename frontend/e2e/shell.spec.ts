import { expect, test } from '@playwright/test';

// Resolved against baseURL: only a same-origin request matches (never http://localhost/…).
const STATUS_URL = '/api/v1/status';

test.describe('application shell', () => {
  test('shows the backend as connected when the API answers', async ({ page }) => {
    await page.route(STATUS_URL, (route) =>
      route.fulfill({
        json: { application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' },
      }),
    );

    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('InvestAI');
    await expect(page.getByTestId('backend-status')).toHaveText('Backend connecté');
  });

  test('shows the backend as unavailable when the API fails', async ({ page, baseURL }) => {
    await page.route(STATUS_URL, (route) => route.fulfill({ status: 503 }));
    const statusRequest = page.waitForRequest(
      (request) => request.url() === new URL(STATUS_URL, baseURL).href,
    );

    await page.goto('/');

    await statusRequest;
    await expect(page.getByTestId('backend-status')).toHaveText('Backend indisponible');
  });
});
