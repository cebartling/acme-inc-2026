import { Formatter, IFormatterOptions } from '@cucumber/cucumber';
import * as messages from '@cucumber/messages';

/**
 * Custom progress formatter that shows meaningful test execution progress.
 * Displays: current scenario, pass/fail status, and running totals.
 * Writes directly to stdout for real-time output.
 */
export default class ProgressFormatter extends Formatter {
  private totalScenarios = 0;
  private completedScenarios = 0;
  private passedScenarios = 0;
  private failedScenarios = 0;
  private currentScenario = '';
  private pickleIdToName = new Map<string, string>();
  private testCaseIdToPickleId = new Map<string, string>();
  private startTime = Date.now();

  // ANSI color codes
  private colors = {
    reset: '\x1b[0m',
    green: '\x1b[32m',
    red: '\x1b[31m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    cyan: '\x1b[36m',
    dim: '\x1b[2m',
    bold: '\x1b[1m',
  };

  constructor(options: IFormatterOptions) {
    super(options);

    options.eventBroadcaster.on('envelope', (envelope: messages.Envelope) => {
      this.parseEnvelope(envelope);
    });
  }

  // Write directly to stdout for real-time progress output
  private stdout(text: string): void {
    process.stdout.write(text);
  }

  private parseEnvelope(envelope: messages.Envelope): void {
    if (envelope.pickle) {
      this.onPickle(envelope.pickle);
    } else if (envelope.testCase) {
      this.onTestCase(envelope.testCase);
    } else if (envelope.testCaseStarted) {
      this.onTestCaseStarted(envelope.testCaseStarted);
    } else if (envelope.testCaseFinished) {
      this.onTestCaseFinished(envelope.testCaseFinished);
    } else if (envelope.testRunFinished) {
      this.onTestRunFinished(envelope.testRunFinished);
    }
  }

  private onPickle(pickle: messages.Pickle): void {
    // Just store the name mapping, don't count here (pickles aren't filtered)
    if (pickle.id) {
      this.pickleIdToName.set(pickle.id, pickle.name || 'Unnamed Scenario');
    }
  }

  private onTestCase(testCase: messages.TestCase): void {
    // Count test cases - these ARE filtered by tags/paths
    this.totalScenarios++;
    if (testCase.id && testCase.pickleId) {
      this.testCaseIdToPickleId.set(testCase.id, testCase.pickleId);
    }
  }

  private onTestCaseStarted(testCaseStarted: messages.TestCaseStarted): void {
    const pickleId = this.testCaseIdToPickleId.get(testCaseStarted.testCaseId);
    if (pickleId) {
      this.currentScenario = this.pickleIdToName.get(pickleId) || 'Unknown';
    }

    const progress = `[${this.completedScenarios + 1}/${this.totalScenarios}]`;
    this.stdout(
      `${this.colors.cyan}${progress}${this.colors.reset} ${this.colors.dim}Running:${this.colors.reset} ${this.currentScenario}\n`
    );
  }

  private onTestCaseFinished(testCaseFinished: messages.TestCaseFinished): void {
    if (testCaseFinished.willBeRetried) {
      return; // Don't count retries
    }

    this.completedScenarios++;

    const testCaseAttempt = this.eventDataCollector.getTestCaseAttempt(
      testCaseFinished.testCaseStartedId
    );

    // stepResults is a Record<string, TestStepResult>, convert to array
    const stepResultsArray = Object.values(testCaseAttempt.stepResults);
    const worstResult = messages.getWorstTestStepResult(stepResultsArray);

    const passed = worstResult.status === messages.TestStepResultStatus.PASSED;

    if (passed) {
      this.passedScenarios++;
      this.stdout(`        ${this.colors.green}\u2713 Passed${this.colors.reset}\n`);
    } else {
      this.failedScenarios++;
      const statusName = messages.TestStepResultStatus[worstResult.status];
      this.stdout(
        `        ${this.colors.red}\u2717 ${statusName}${this.colors.reset}\n`
      );
    }
  }

  private onTestRunFinished(_testRunFinished: messages.TestRunFinished): void {
    const duration = Date.now() - this.startTime;
    const minutes = Math.floor(duration / 60000);
    const seconds = ((duration % 60000) / 1000).toFixed(1);

    this.stdout('\n');
    this.stdout(
      `${this.colors.bold}${this.colors.blue}═══════════════════════════════════════════════════════════════${this.colors.reset}\n`
    );
    this.stdout(
      `${this.colors.bold}  Test Run Summary${this.colors.reset}\n`
    );
    this.stdout(
      `${this.colors.blue}═══════════════════════════════════════════════════════════════${this.colors.reset}\n`
    );
    this.stdout('\n');
    this.stdout(
      `  ${this.colors.bold}Total:${this.colors.reset}    ${this.totalScenarios} scenarios\n`
    );
    this.stdout(
      `  ${this.colors.green}Passed:${this.colors.reset}   ${this.passedScenarios} scenarios\n`
    );
    if (this.failedScenarios > 0) {
      this.stdout(
        `  ${this.colors.red}Failed:${this.colors.reset}   ${this.failedScenarios} scenarios\n`
      );
    }
    this.stdout(
      `  ${this.colors.dim}Duration:${this.colors.reset} ${minutes}m ${seconds}s\n`
    );
    this.stdout('\n');

    if (this.failedScenarios === 0) {
      this.stdout(
        `  ${this.colors.green}${this.colors.bold}\u2713 All scenarios passed!${this.colors.reset}\n`
      );
    } else {
      this.stdout(
        `  ${this.colors.red}${this.colors.bold}\u2717 ${this.failedScenarios} scenario(s) failed${this.colors.reset}\n`
      );
    }
    this.stdout('\n');
  }
}
