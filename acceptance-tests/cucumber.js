const common = {
  import: [
    // Support files
    'support/world.ts',
    'support/hooks.ts',
    'support/test-data.ts',
    // Common step definitions
    'steps/common/assertions.steps.ts',
    'steps/common/navigation.steps.ts',
    'steps/common/api-assertions.steps.ts',
    // Customer step definitions
    'steps/customer/registration.steps.ts',
    // API step definitions
    'steps/api/address-api.steps.ts',
    'steps/api/authentication-api.steps.ts',
    'steps/api/consent-api.steps.ts',
    'steps/api/customer-activation.steps.ts',
    'steps/api/customer-profile.steps.ts',
    'steps/api/email-verification.steps.ts',
    'steps/api/mfa-verification.steps.ts',
    'steps/api/preferences-api.steps.ts',
    'steps/api/profile-completeness.steps.ts',
    'steps/api/registration-api.steps.ts',
    'steps/api/session-token-creation.steps.ts',
    'steps/api/verification-email.steps.ts',
  ],
  format: [
    'json:reports/cucumber-report.json',
    'html:reports/cucumber-report.html',
    './support/progress-formatter.ts',
  ],
  formatOptions: {
    snippetInterface: 'async-await',
  },
  publishQuiet: true,
};

export default {
  default: {
    ...common,
    paths: ['features/**/*.feature'],
    // Exclude @rate-limiting tests by default since rate limiting is disabled in local dev
    tags: 'not @rate-limiting',
  },
  smoke: {
    ...common,
    paths: ['features/**/*.feature'],
    tags: '@smoke and not @rate-limiting',
  },
  regression: {
    ...common,
    paths: ['features/**/*.feature'],
    tags: '@regression and not @rate-limiting',
  },
  customer: {
    ...common,
    paths: ['features/customer/**/*.feature'],
  },
};
