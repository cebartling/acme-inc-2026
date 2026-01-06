const common = {
  import: [
    // Support files
    'support/world.ts',
    'support/hooks.ts',
    'support/test-data.ts',
    // Common step definitions
    'steps/common/assertions.steps.ts',
    'steps/common/navigation.steps.ts',
    // Customer step definitions
    'steps/customer/registration.steps.ts',
    // API step definitions
    'steps/api/registration-api.steps.ts',
    'steps/api/customer-profile.steps.ts',
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
  },
  smoke: {
    ...common,
    paths: ['features/**/*.feature'],
    tags: '@smoke',
  },
  regression: {
    ...common,
    paths: ['features/**/*.feature'],
    tags: '@regression',
  },
  customer: {
    ...common,
    paths: ['features/customer/**/*.feature'],
  },
};
