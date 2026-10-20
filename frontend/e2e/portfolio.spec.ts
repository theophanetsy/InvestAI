import { expect, test } from '@playwright/test';
import { mockApi } from './support/api-mock';
import { expectFocusRing, tabTo } from './support/layout';
import { mockPortfolioApi } from './support/portfolio-mock';

test.describe('profile and declared portfolio', () => {
  test('fills the profile, adds a line found by search and sees its valuation', async ({
    page,
  }) => {
    await mockApi(page, { loggedIn: true });
    const requests = await mockPortfolioApi(page);

    // Profile
    await page.goto('/profil');
    await expect(page).toHaveTitle('Mon profil · InvestAI');
    await expect(page.getByTestId('profile-state')).toContainText('Aucun profil enregistré');
    await page.getByLabel('Somme à placer').fill('20 000');
    await page.getByLabel('Horizon de placement').fill('10');
    await page.getByRole('radio', { name: 'Équilibré' }).check();
    await expect(page.getByTestId('risk-description')).toHaveText(
      'Baisse annuelle supportable : 20 %.',
    );
    const exclusions = page.locator('inv-exclusions-field input');
    await exclusions.fill('Tabac');
    await exclusions.press('Enter');
    await page.getByLabel('Vos objectifs').fill('<b>Préparer ma retraite</b>');
    await page.getByRole('button', { name: 'Enregistrer le profil' }).click();

    await expect(page.getByRole('status')).toHaveText('Profil enregistré.');
    expect(requests.savedProfile).toEqual({
      amountToInvest: '20000',
      horizonYears: 10,
      riskProfile: 'BALANCED',
      exclusions: ['Tabac'],
      objectives: '<b>Préparer ma retraite</b>',
    });
    await expect(page.locator('b')).toHaveCount(0);

    // Portfolio
    await page
      .getByRole('navigation', { name: 'Navigation principale' })
      .getByRole('link', { name: 'Mon portefeuille' })
      .click();
    await expect(page).toHaveURL('/portefeuille');
    await expect(page.getByTestId('portfolio-empty')).toBeVisible();
    const search = page.getByRole('combobox', { name: 'Valeur (nom, ISIN ou ticker)' });
    await search.fill('air');
    const option = page.getByRole('option', { name: /Air Liquide/ });
    await expect(option).toContainText('PEA probable');
    // Chosen with the keyboard, as a keyboard user would.
    await search.press('ArrowDown');
    await search.press('Enter');
    await expect(page.getByTestId('selected-instrument')).toContainText('Air Liquide (AI');
    await page.getByLabel('Quantité').fill('10');
    await page.getByLabel('Prix moyen d’achat').fill('150,1234');
    await page.getByRole('button', { name: 'Ajouter la ligne' }).click();

    const row = page.getByRole('row', { name: /Air Liquide/ });
    await expect(row).toContainText(/173,16.€ au 16\/10\/2026/);
    await expect(row).toContainText(/1.731,60.€/);
    await expect(row).toContainText(/\+230,37.€ \(\+15,35.%\)/);
    await expect(page.getByTestId('portfolio-total')).toHaveText(/^1.731,60.€$/);
    expect(requests.addedLine).toEqual({
      instrumentId: '7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c',
      quantity: 10,
      averagePrice: '150.1234',
    });
    await expect(page.getByText('Ligne ajoutée.')).toBeVisible();

    // Deletion, reached with the keyboard and confirmed
    const remove = page.getByRole('button', { name: 'Supprimer Air Liquide' });
    await tabTo(page, remove, 40);
    await expectFocusRing(remove);
    await page.keyboard.press('Enter');
    const confirm = page.getByRole('dialog', { name: 'Supprimer la ligne ?' });
    await expect(confirm).toContainText('Air Liquide sera retirée du portefeuille déclaré.');
    await confirm.getByRole('button', { name: 'Supprimer' }).click();

    await expect(page.getByTestId('portfolio-empty')).toBeVisible();
    expect(requests.deletedLine).toBe('l1');
    await expect(page.getByText('Ligne supprimée.')).toBeVisible();
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('keeps the portfolio page within the screen width', async ({ page }) => {
      await mockApi(page, { loggedIn: true });
      await mockPortfolioApi(page);

      await page.goto('/portefeuille');

      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Mon portefeuille');
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        390,
      );
    });
  });
});
