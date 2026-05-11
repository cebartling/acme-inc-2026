import { describe, it, expect } from "vitest";
import { UserMenu } from "./UserMenu";

/**
 * Note: component-rendering tests for UserMenu are deferred because of the
 * same React 19 + Vitest "useState is null" issue documented in
 * PasswordInput.test.tsx. The end-to-end behavior (open menu, click Sign
 * Out, confirm Sign Out All Devices) is exercised by
 * acceptance-tests/features/customer/customer-logout-ui.feature.
 *
 * TODO: once the test infra supports useState in render(), add:
 *   - renders nothing when there is no authenticated user
 *   - "Sign Out" item calls logout(false)
 *   - "Sign Out All Devices" opens the confirm dialog
 *   - confirming the dialog calls logout(true)
 *   - canceling the dialog does not call logout
 */
describe("UserMenu", () => {
  it("is exported as a function component", () => {
    expect(typeof UserMenu).toBe("function");
  });
});
