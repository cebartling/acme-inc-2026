import { describe, it, expect } from "vitest";
import { useLogout } from "./useLogout";

/**
 * Note: full hook-rendering tests for useLogout are deferred because of the
 * known "Cannot read properties of null (reading 'useState')" failure in this
 * project's React 19 + Vitest + @testing-library/react setup (see the same
 * note in PasswordInput.test.tsx). End-to-end coverage of the logout flow
 * lives in acceptance-tests/features/api/customer-logout-api.feature and
 * acceptance-tests/features/customer/customer-logout-ui.feature.
 *
 * TODO: re-enable the suites below once the test infra supports renderHook
 * for hooks that call useState. The intended coverage is:
 *   - logout(false) calls identityApi.logout, clears stores, emits analytics,
 *     and navigates to /signin?logout=true
 *   - logout(true) calls identityApi.logoutAll with the same client cleanup
 *   - failure paths still clear state and redirect
 *   - isLoading is true while the API call is in flight
 */
describe("useLogout", () => {
  it("is exported as a function", () => {
    expect(typeof useLogout).toBe("function");
  });
});
