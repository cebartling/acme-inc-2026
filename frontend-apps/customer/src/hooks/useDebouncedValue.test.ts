import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useDebouncedValue } from "./useDebouncedValue";

describe("useDebouncedValue", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns initial value immediately", () => {
    const { result } = renderHook(() => useDebouncedValue("hello", 150));
    expect(result.current).toBe("hello");
  });

  it("does not update before delay elapses", () => {
    const { result, rerender } = renderHook(
      ({ value, delay }) => useDebouncedValue(value, delay),
      { initialProps: { value: "hello", delay: 150 } },
    );

    rerender({ value: "world", delay: 150 });
    act(() => {
      vi.advanceTimersByTime(100);
    });

    expect(result.current).toBe("hello");
  });

  it("updates after delay elapses", () => {
    const { result, rerender } = renderHook(
      ({ value, delay }) => useDebouncedValue(value, delay),
      { initialProps: { value: "hello", delay: 150 } },
    );

    rerender({ value: "world", delay: 150 });
    act(() => {
      vi.advanceTimersByTime(150);
    });

    expect(result.current).toBe("world");
  });

  it("resets timer on rapid changes and only emits the last value", () => {
    const { result, rerender } = renderHook(
      ({ value, delay }) => useDebouncedValue(value, delay),
      { initialProps: { value: "a", delay: 150 } },
    );

    rerender({ value: "ab", delay: 150 });
    act(() => {
      vi.advanceTimersByTime(100);
    });

    rerender({ value: "abc", delay: 150 });
    act(() => {
      vi.advanceTimersByTime(150);
    });

    expect(result.current).toBe("abc");
  });
});
