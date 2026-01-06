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
    'steps/customer/authentication.steps.ts',
    'steps/customer/checkout.steps.ts',
    'steps/customer/product-catalog.steps.ts',
    'steps/customer/registration.steps.ts',
    'steps/customer/shopping-cart.steps.ts',
    // Admin step definitions
    'steps/admin/order-management.steps.ts',
    'steps/admin/product-management.steps.ts',
    'steps/admin/user-management.steps.ts',
  ],
  format: [
    './support/progress-formatter.ts',
    'json:reports/cucumber-report.json',
    'html:reports/cucumber-report.html',
    ['allure-cucumberjs/reporter', 'allure-results'],
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
  admin: {
    ...common,
    paths: ['features/admin/**/*.feature'],
  },
};
