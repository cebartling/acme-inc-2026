import { useRef, useState, useEffect, useCallback, KeyboardEvent, ClipboardEvent } from "react";
import { Input } from "@/components/ui/input";

export interface OtpInputProps {
  /** Number of digits in the OTP (default: 6) */
  length?: number;
  /** Called when all digits are entered */
  onComplete: (code: string) => void;
  /** Whether the input is disabled */
  disabled?: boolean;
  /** Auto-focus the first input on mount */
  autoFocus?: boolean;
  /** Error state for styling */
  error?: boolean;
}

/**
 * OTP (One-Time Password) input component for MFA verification.
 *
 * Features:
 * - Auto-advances to next input on entry
 * - Backspace navigates to previous input
 * - Paste support for full code
 * - Auto-submits when all digits entered
 * - Numeric-only input
 */
export function OtpInput({
  length = 6,
  onComplete,
  disabled = false,
  autoFocus = true,
  error = false,
}: OtpInputProps) {
  const [values, setValues] = useState<string[]>(Array(length).fill(""));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Initialize refs array
  useEffect(() => {
    inputRefs.current = inputRefs.current.slice(0, length);
  }, [length]);

  // Auto-focus first input on mount
  useEffect(() => {
    if (autoFocus && inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, [autoFocus]);

  // Check if all values are filled and trigger onComplete
  const checkComplete = useCallback(
    (newValues: string[]) => {
      const code = newValues.join("");
      if (code.length === length && newValues.every((v) => v !== "")) {
        onComplete(code);
      }
    },
    [length, onComplete]
  );

  const handleChange = (index: number, value: string) => {
    // Only accept single digit
    const digit = value.replace(/\D/g, "").slice(-1);

    const newValues = [...values];
    newValues[index] = digit;
    setValues(newValues);

    // Auto-advance to next input
    if (digit && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    checkComplete(newValues);
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    // Backspace: clear current and go to previous
    if (e.key === "Backspace") {
      if (values[index] === "" && index > 0) {
        // If current is empty, go to previous
        inputRefs.current[index - 1]?.focus();
      } else {
        // Clear current
        const newValues = [...values];
        newValues[index] = "";
        setValues(newValues);
      }
    }

    // Arrow keys for navigation
    if (e.key === "ArrowLeft" && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
    if (e.key === "ArrowRight" && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData("text").replace(/\D/g, "");

    if (pastedData.length > 0) {
      const newValues = [...values];
      for (let i = 0; i < Math.min(pastedData.length, length); i++) {
        newValues[i] = pastedData[i];
      }
      setValues(newValues);

      // Focus the appropriate input
      const focusIndex = Math.min(pastedData.length, length - 1);
      inputRefs.current[focusIndex]?.focus();

      checkComplete(newValues);
    }
  };

  const handleFocus = (index: number) => {
    // Select the input content on focus
    inputRefs.current[index]?.select();
  };

  return (
    <div className="flex justify-center gap-2" role="group" aria-label="One-time password input">
      {values.map((value, index) => (
        <Input
          key={index}
          ref={(el) => {
            inputRefs.current[index] = el;
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={1}
          value={value}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          onFocus={() => handleFocus(index)}
          disabled={disabled}
          aria-label={`Digit ${index + 1} of ${length}`}
          className={`
            w-12 h-14 text-center text-2xl font-mono
            ${error ? "border-red-500 focus:border-red-500 focus:ring-red-500" : ""}
            ${disabled ? "opacity-50 cursor-not-allowed" : ""}
          `}
        />
      ))}
    </div>
  );
}
