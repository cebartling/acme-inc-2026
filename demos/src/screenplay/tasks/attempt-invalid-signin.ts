import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';

const AttemptWrongPassword = (wrongPassword: string, attemptNumber: number) =>
  Interaction.where(
    the`#actor submits wrong password (attempt ${attemptNumber})`,
    async (actor) => {
      const page = await BrowseTheWeb.as(actor).currentPage();
      const nativePage = await (page as any).nativePage();

      console.log(`  attempt ${attemptNumber}: submitting wrong password...`);
      const passwordInput = nativePage.locator('input[id="password"]');
      await passwordInput.fill(wrongPassword);
      await passwordInput.blur();
      await nativePage.waitForTimeout(400);
      await nativePage.getByRole('button', { name: /sign in/i }).click();

      await nativePage.getByTestId('signin-error-banner').waitFor({ timeout: 5000 });
      await nativePage.getByTestId('signin-remaining-attempts').waitFor({ timeout: 5000 });
    }
  );

const HoverResetLink = Interaction.where(
  the`#actor hovers the reset password link`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('signin-error-reset-link').hover();
  }
);

export const AttemptInvalidSignin = {
  showingProgressiveWarnings: (email: string) => {
    const wrongPassword = 'WrongPassword123!';

    return Task.where(
      the`#actor demonstrates invalid credentials UX for ${email}`,
      Interaction.where(the`#actor fills email`, async (actor) => {
        const page = await BrowseTheWeb.as(actor).currentPage();
        const nativePage = await (page as any).nativePage();
        await nativePage.goto(`${config.customerAppUrl}/signin`);
        await nativePage.getByRole('heading', { name: /welcome back/i }).waitFor();
        const emailInput = nativePage.getByRole('textbox', { name: 'Email' });
        await emailInput.fill(email);
        await emailInput.blur();
        await nativePage.waitForTimeout(400);
      }),

      AttemptWrongPassword(wrongPassword, 1),
      Wait.for(Duration.ofMilliseconds(2000)),

      AttemptWrongPassword(wrongPassword, 2),
      Wait.for(Duration.ofMilliseconds(2000)),

      AttemptWrongPassword(wrongPassword, 3),
      Wait.for(Duration.ofMilliseconds(3000)),

      HoverResetLink,
      Wait.for(Duration.ofMilliseconds(1500))
    );
  },
};
