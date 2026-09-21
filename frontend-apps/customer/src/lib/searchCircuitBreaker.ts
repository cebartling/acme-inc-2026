/**
 * Client-side circuit breaker for the product search endpoint (US-0004-09).
 *
 * Search failures must not leave the customer staring at a blank results area. After
 * `threshold` consecutive failures the breaker opens and subsequent searches fail fast
 * instead of waiting on a service that is known to be down, which is what lets the
 * search page swap in category browsing immediately.
 *
 * States:
 *   CLOSED    - normal operation; failures are counted.
 *   OPEN      - calls are rejected without touching the network.
 *   HALF_OPEN - a single probe is allowed through to test recovery.
 *
 * Unlike the sketch in the user story, `execute` rethrows rather than swallowing the
 * error into a fallback. The search fallback renders a different component tree from a
 * different endpoint, so funnelling it through the same typed promise as a SearchResponse
 * would be a lie. Callers catch and decide what to render.
 */

export type CircuitState = "CLOSED" | "OPEN" | "HALF_OPEN";

/** Thrown when the breaker rejects a call without attempting it. */
export class CircuitOpenError extends Error {
  constructor() {
    super("Search is unavailable: circuit breaker is open");
    this.name = "CircuitOpenError";
  }
}

export interface CircuitBreakerOptions {
  /** Consecutive failures required to open the circuit. */
  threshold?: number;
  /** How long the circuit stays open before a probe is allowed, in ms. */
  resetTimeoutMs?: number;
  /** Injectable clock, so tests do not depend on wall-clock time. */
  now?: () => number;
}

export class CircuitBreaker {
  private state: CircuitState = "CLOSED";
  private failureCount = 0;
  private nextProbeAt = 0;

  /**
   * The in-flight HALF_OPEN probe, if any.
   *
   * Without this, every queued search fires its own probe the moment the reset window
   * elapses — stampeding a service that has not recovered yet. Concurrent callers share
   * the single probe's outcome instead.
   */
  private probe: Promise<unknown> | null = null;

  private readonly threshold: number;
  private readonly resetTimeoutMs: number;
  private readonly now: () => number;

  constructor(options: CircuitBreakerOptions = {}) {
    this.threshold = options.threshold ?? 5;
    this.resetTimeoutMs = options.resetTimeoutMs ?? 30_000;
    this.now = options.now ?? Date.now;
  }

  getState(): CircuitState {
    // OPEN lapses into HALF_OPEN purely by the passage of time, so reading the state
    // has to account for an elapsed reset window rather than waiting for a timer.
    if (this.state === "OPEN" && this.now() >= this.nextProbeAt) {
      this.state = "HALF_OPEN";
    }
    return this.state;
  }

  /** True when calls are being rejected outright. */
  isOpen(): boolean {
    return this.getState() === "OPEN";
  }

  getFailureCount(): number {
    return this.failureCount;
  }

  /**
   * Runs `fn` under the breaker.
   *
   * @param fn The call to guard.
   * @param isFailure Decides whether a rejection reflects an unhealthy service. Errors
   *   it rejects are rethrown without touching the breaker's state — a request the
   *   service refused on its merits says nothing about the service's health. Defaults
   *   to counting every rejection.
   * @throws CircuitOpenError when the circuit is open, without calling `fn`.
   * @throws whatever `fn` rejects with, after recording the failure.
   */
  async execute<T>(
    fn: () => Promise<T>,
    isFailure: (error: unknown) => boolean = () => true,
  ): Promise<T> {
    const state = this.getState();

    if (state === "OPEN") {
      throw new CircuitOpenError();
    }

    if (state === "HALF_OPEN") {
      // Join the probe already in flight rather than starting a second one.
      if (this.probe) {
        await this.probe.catch(() => {
          // The shared probe's failure is reported below via the breaker state; this
          // caller still needs its own rejection, which the recursive call produces.
        });
        return this.execute(fn, isFailure);
      }

      const probe = fn();
      this.probe = probe;
      try {
        const result = await probe;
        this.onSuccess();
        return result;
      } catch (error) {
        if (isFailure(error)) {
          this.onFailure();
        }
        throw error;
      } finally {
        this.probe = null;
      }
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      if (isFailure(error)) {
        this.onFailure();
      }
      throw error;
    }
  }

  /** Records a success: the circuit closes and the failure count resets. */
  onSuccess(): void {
    this.failureCount = 0;
    this.state = "CLOSED";
    this.nextProbeAt = 0;
  }

  /**
   * Records a failure. Opens the circuit once the threshold is reached, or immediately
   * if a HALF_OPEN probe failed.
   */
  onFailure(): void {
    this.failureCount++;

    if (this.state === "HALF_OPEN" || this.failureCount >= this.threshold) {
      this.state = "OPEN";
      this.nextProbeAt = this.now() + this.resetTimeoutMs;
    }
  }

  /** Returns the breaker to its initial state. */
  reset(): void {
    this.state = "CLOSED";
    this.failureCount = 0;
    this.nextProbeAt = 0;
    this.probe = null;
  }
}

/**
 * The breaker guarding `productApi.search`.
 *
 * Module-scoped so every caller shares one view of search health, mirroring the
 * token-refresh state in `services/api.ts`.
 */
export const searchCircuitBreaker = new CircuitBreaker();

/**
 * Test-only: reset the shared breaker between vitest cases so module-level state does
 * not leak. NOT used by application code — mirrors `__resetRefreshStateForTests`.
 */
export function __resetCircuitBreakerForTests(): void {
  searchCircuitBreaker.reset();
}
