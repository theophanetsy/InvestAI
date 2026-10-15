import type { Page } from '@playwright/test';
import { expect, test } from '@playwright/test';
import { API, PASSWORD, mockApi } from './support/api-mock';

async function logIn(page: Page, password: string): Promise<void> {
  await page.getByLabel('Identifiant').fill('owner');
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test.describe('authentication', () => {
  test('logs in from a protected page, comes back to it, then logs out', async ({ page }) => {
    await mockApi(page, { loggedIn: false });

    await page.goto('/simulation');

    await expect(page).toHaveURL('/login?returnUrl=%2Fsimulation');
    await expect(page).toHaveTitle('Connexion · InvestAI');
    await logIn(page, 'wrong');
    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect.');

    await logIn(page, PASSWORD);

    await expect(page).toHaveURL('/simulation');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Simulation');

    await page.getByRole('button', { name: 'Se déconnecter' }).click();

    await expect(page).toHaveURL('/login');
    await page.goto('/dashboard');
    await expect(page).toHaveURL('/login?returnUrl=%2Fdashboard');
  });

  test('logs in again right after a logout, which deleted the CSRF cookie', async ({ page }) => {
    await mockApi(page, { loggedIn: true });
    await page.goto('/dashboard');
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL('/login');
    const renewal = page.waitForRequest((request) => request.url().endsWith(API.status));

    await logIn(page, PASSWORD);

    await renewal;
    await expect(page).toHaveURL('/dashboard');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tableau de bord');
  });

  test('requires both fields', async ({ page }) => {
    await mockApi(page, { loggedIn: false });
    await page.goto('/login');

    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByText("L'identifiant est obligatoire.")).toBeVisible();
    await expect(page.getByText('Le mot de passe est obligatoire.')).toBeVisible();
  });

  test('says when the logout failed, with its reference', async ({ page }) => {
    await mockApi(page, { loggedIn: true, logoutStatus: 500 });
    await page.goto('/dashboard');

    await page.getByRole('button', { name: 'Se déconnecter' }).click();

    await expect(page.getByText('La déconnexion a échoué. Référence : e2e-req')).toBeVisible();
    await expect(page).toHaveURL('/dashboard');
  });
});
