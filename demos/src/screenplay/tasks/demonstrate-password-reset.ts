import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';
import { getPasswordResetToken } from '../../identity-api.ts';

const SubmitForgotPassword = (email: string) =>
  Interaction.where(the`#actor requests a password reset for ${email}`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    console.log('\n  scene 1: /forgot-password — submit email, see generic confirmation');
    await nativePage.goto(`${config.customerAppUrl}/forgot-password`);
    await nativePage.getByRole('heading', { name: /forgot your password/i }).waitFor();
    await nativePage.waitForTimeout(1000);

    const emailInput = nativePage.getByTestId('forgot-password-email-input');
    await emailInput.fill(email);
    await emailInput.blur();
    await nativePage.waitForTimeout(600);

    await nativePage.getByTestId('forgot-password-submit').click();
    await nativePage.getByTestId('forgot-password-sent').waitFor({ timeout: 8000 });
    console.log('  confirmation banner visible');
  });

const ShowExpiredLink = Interaction.where(
  the`#actor demonstrates an expired reset link`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    console.log('\n  scene 2: expired reset link — "invalid or expired" error card');
    await nativePage.goto(
      `${config.customerAppUrl}/reset-password?token=rst_this_token_is_obviously_invalid`
    );
    await nativePage.getByTestId('reset-password-expired').waitFor({ timeout: 8000 });
    console.log('  expired-link card visible');
    await nativePage.waitForTimeout(2500);

    await nativePage.getByRole('link', { name: /request a new link/i }).hover();
  }
);

const ResetPasswordWithValidToken = (userId: string, newPassword: string) =>
  Interaction.where(the`#actor resets the password with a valid token`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    console.log('\n  scene 3: happy-path reset with a valid fixture token');
    const resetToken = await getPasswordResetToken(userId);
    console.log(`  reset token obtained: ${resetToken.slice(0, 20)}…`);

    await nativePage.goto(
      `${config.customerAppUrl}/reset-password?token=${encodeURIComponent(resetToken)}`
    );
    await nativePage.getByTestId('reset-password-form').waitFor({ timeout: 8000 });
    console.log('  reset-password form ready');
    await nativePage.waitForTimeout(1000);

    const newPasswordInput = nativePage.getByTestId('reset-password-new-input');
    const confirmInput = nativePage.getByTestId('reset-password-confirm-input');

    await newPasswordInput.fill(newPassword);
    await newPasswordInput.blur();
    await nativePage.waitForTimeout(500);

    await confirmInput.fill(newPassword);
    await confirmInput.blur();
    await nativePage.waitForTimeout(600);

    await nativePage.getByTestId('reset-password-submit').click();
    await nativePage.getByTestId('reset-password-success').waitFor({ timeout: 8000 });
    console.log('  "Password updated" banner visible');
    await nativePage.waitForTimeout(2000);

    await nativePage.getByTestId('reset-password-signin-now').hover();
    await nativePage.waitForTimeout(1500);

    await nativePage.waitForURL((url: URL) => url.pathname.endsWith('/signin'), {
      timeout: 6000,
    });
    console.log('  auto-redirected to /signin');
  });

const SignInWithNewPassword = (email: string, newPassword: string) =>
  Interaction.where(
    the`#actor signs in with the new password to prove the reset worked`,
    async (actor) => {
      const page = await BrowseTheWeb.as(actor).currentPage();
      const nativePage = await (page as any).nativePage();

      console.log('\n  bonus: signing in with the new password to prove the reset worked');
      await nativePage.getByRole('textbox', { name: 'Email' }).fill(email);
      await nativePage.getByRole('textbox', { name: 'Email' }).blur();
      await nativePage.waitForTimeout(400);
      await nativePage.locator('input[id="password"]').fill(newPassword);
      await nativePage.locator('input[id="password"]').blur();
      await nativePage.waitForTimeout(400);
      await nativePage.getByRole('button', { name: /sign in/i }).click();

      await nativePage.waitForURL((url: URL) => !url.pathname.endsWith('/signin'), {
        timeout: 15000,
      });
      console.log('  signed in successfully with the new password');
    }
  );

export const DemonstratePasswordReset = {
  forUser: (userId: string, email: string, newPassword: string) =>
    Task.where(
      the`#actor demonstrates the password reset flow`,
      SubmitForgotPassword(email),
      Wait.for(Duration.ofMilliseconds(3000)),

      ShowExpiredLink,
      Wait.for(Duration.ofMilliseconds(1500)),

      ResetPasswordWithValidToken(userId, newPassword),
      Wait.for(Duration.ofMilliseconds(1000)),

      SignInWithNewPassword(email, newPassword),
      Wait.for(Duration.ofMilliseconds(2500))
    ),
};
