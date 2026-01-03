const common = {
  requireModule: ['ts-node/register'],
  require: ['steps/**/*.ts', 'support/**/*.ts'],
  format: [
    'progress-bar',
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
