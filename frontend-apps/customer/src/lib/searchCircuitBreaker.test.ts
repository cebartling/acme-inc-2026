import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  CircuitBreaker,
  CircuitOpenError,
  searchCircuitBreaker,
  __resetCircuitBreakerForTests,
} from "./searchCircuitBreaker";

/**
 * A controllable clock, so the 30s reset window can be crossed without fake timers
 * or real waiting.
 */
function createClock(start = 1_000_000) {
  let current = start;
  return {
    now: () => current,
    advance: (ms: number) => {
      current += ms;
    },
  };
}

const boom = () => Promise.reject(new Error("service down"));
const ok = () => Promise.resolve("results");

/** Drives the breaker to OPEN by failing it `threshold` times. */
async function openTheCircuit(breaker: CircuitBreaker, times = 5) {
  for (let i = 0; i < times; i++) {
    await expect(breaker.execute(boom)).rejects.toThrow("service down");
  }
}

describe("CircuitBreaker", () => {
  describe("CLOSED state", () => {
    it("passes through results while the service is healthy", async () => {
      const breaker = new CircuitBreaker();

      await expect(breaker.execute(ok)).resolves.toBe("results");
      expect(breaker.getState()).toBe("CLOSED");
      expect(breaker.getFailureCount()).toBe(0);
    });

    it("rethrows the underlying error rather than swallowing it", async () => {
      const breaker = new CircuitBreaker();

      await expect(breaker.execute(boom)).rejects.toThrow("service down");
    });

    it("stays closed at one failure below the threshold", async () => {
      const breaker = new CircuitBreaker({ threshold: 5 });

      await openTheCircuit(breaker, 4);

      expect(breaker.getState()).toBe("CLOSED");
      expect(breaker.getFailureCount()).toBe(4);
    });

    it("opens exactly on the fifth consecutive failure", async () => {
      const breaker = new CircuitBreaker({ threshold: 5 });

      await openTheCircuit(breaker, 5);

      expect(breaker.getState()).toBe("OPEN");
      expect(breaker.isOpen()).toBe(true);
    });

    it("resets the failure count after an intervening success", async () => {
      const breaker = new CircuitBreaker({ threshold: 5 });

      await openTheCircuit(breaker, 4);
      await expect(breaker.execute(ok)).resolves.toBe("results");
      expect(breaker.getFailureCount()).toBe(0);

      // Four more failures must not open it: the counter restarted.
      await openTheCircuit(breaker, 4);
      expect(breaker.getState()).toBe("CLOSED");
    });
  });

  describe("OPEN state", () => {
    it("rejects with CircuitOpenError without invoking the call", async () => {
      const breaker = new CircuitBreaker({ threshold: 5 });
      await openTheCircuit(breaker);

      const fn = vi.fn(ok);
      await expect(breaker.execute(fn)).rejects.toBeInstanceOf(
        CircuitOpenError,
      );
      expect(fn).not.toHaveBeenCalled();
    });

    it("stays open until the reset window elapses", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);

      clock.advance(29_999);
      expect(breaker.getState()).toBe("OPEN");

      const fn = vi.fn(ok);
      await expect(breaker.execute(fn)).rejects.toBeInstanceOf(
        CircuitOpenError,
      );
      expect(fn).not.toHaveBeenCalled();
    });

    it("transitions to HALF_OPEN once the reset window elapses", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);

      clock.advance(30_000);

      expect(breaker.getState()).toBe("HALF_OPEN");
      expect(breaker.isOpen()).toBe(false);
    });
  });

  describe("HALF_OPEN state", () => {
    it("forwards the next call as a probe", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);
      clock.advance(30_000);

      const fn = vi.fn(ok);
      await expect(breaker.execute(fn)).resolves.toBe("results");
      expect(fn).toHaveBeenCalledTimes(1);
    });

    it("closes the circuit when the probe succeeds", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);
      clock.advance(30_000);

      await breaker.execute(ok);

      expect(breaker.getState()).toBe("CLOSED");
      expect(breaker.getFailureCount()).toBe(0);
    });

    it("re-opens the circuit when the probe fails, without waiting for the threshold", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);
      clock.advance(30_000);

      await expect(breaker.execute(boom)).rejects.toThrow("service down");

      expect(breaker.getState()).toBe("OPEN");
    });

    it("restarts the reset window when the probe fails", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);

      clock.advance(30_000);
      await expect(breaker.execute(boom)).rejects.toThrow("service down");

      // Almost a full window after the *failed probe*, not after the original open.
      clock.advance(29_999);
      expect(breaker.getState()).toBe("OPEN");

      clock.advance(1);
      expect(breaker.getState()).toBe("HALF_OPEN");
    });

    it("sends only one probe when several searches race", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);
      clock.advance(30_000);

      // Only the probe itself is held open; any later call resolves immediately, so the
      // test observes how many times the service was actually hit.
      let release: (value: string) => void = () => {};
      const fn = vi.fn(() =>
        fn.mock.calls.length === 1
          ? new Promise<string>((resolve) => {
              release = resolve;
            })
          : Promise.resolve("results"),
      );

      const first = breaker.execute(fn);
      const second = breaker.execute(fn);

      release("results");

      await expect(first).resolves.toBe("results");
      await second;

      // One probe, plus the second caller's own call once the circuit closed.
      expect(fn).toHaveBeenCalledTimes(2);
      expect(breaker.getState()).toBe("CLOSED");
    });

    it("rejects a racing caller when the shared probe fails", async () => {
      const clock = createClock();
      const breaker = new CircuitBreaker({
        resetTimeoutMs: 30_000,
        now: clock.now,
      });
      await openTheCircuit(breaker);
      clock.advance(30_000);

      let fail: (err: Error) => void = () => {};
      const fn = vi.fn(
        () =>
          new Promise<string>((_resolve, reject) => {
            fail = reject;
          }),
      );

      const first = breaker.execute(fn);
      const second = breaker.execute(fn);

      fail(new Error("service down"));

      await expect(first).rejects.toThrow("service down");
      await expect(second).rejects.toBeInstanceOf(CircuitOpenError);

      // The racing caller must not have retried the downed service.
      expect(fn).toHaveBeenCalledTimes(1);
      expect(breaker.getState()).toBe("OPEN");
    });
  });

  describe("configuration", () => {
    it("honours a custom threshold", async () => {
      const breaker = new CircuitBreaker({ threshold: 2 });

      await openTheCircuit(breaker, 2);

      expect(breaker.getState()).toBe("OPEN");
    });

    it("reset() returns the breaker to CLOSED", async () => {
      const breaker = new CircuitBreaker({ threshold: 5 });
      await openTheCircuit(breaker);
      expect(breaker.getState()).toBe("OPEN");

      breaker.reset();

      expect(breaker.getState()).toBe("CLOSED");
      expect(breaker.getFailureCount()).toBe(0);
    });
  });
});

describe("searchCircuitBreaker (shared instance)", () => {
  beforeEach(() => {
    __resetCircuitBreakerForTests();
  });

  it("starts closed", () => {
    expect(searchCircuitBreaker.getState()).toBe("CLOSED");
  });

  it("opens after five consecutive failures", async () => {
    await openTheCircuit(searchCircuitBreaker, 5);

    expect(searchCircuitBreaker.getState()).toBe("OPEN");
  });

  it("is reset between tests by __resetCircuitBreakerForTests", () => {
    // Depends on the preceding test having opened the shared breaker.
    expect(searchCircuitBreaker.getState()).toBe("CLOSED");
    expect(searchCircuitBreaker.getFailureCount()).toBe(0);
  });
});
