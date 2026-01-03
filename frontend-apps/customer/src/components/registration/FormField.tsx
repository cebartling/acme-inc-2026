import { Check } from 'lucide-react';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

interface FormFieldProps {
  label: string;
  htmlFor: string;
  error?: string;
  touched?: boolean;
  valid?: boolean;
  required?: boolean;
  characterCount?: { current: number; max: number };
  children: React.ReactNode;
  className?: string;
}

export function FormField({
  label,
  htmlFor,
  error,
  touched,
  valid,
  required,
  characterCount,
  children,
  className,
}: FormFieldProps) {
  const showError = touched && error;
  const showSuccess = touched && valid && !error;

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex items-center justify-between">
        <Label
          htmlFor={htmlFor}
          className={cn(
            'text-sm font-medium',
            showError && 'text-red-600'
          )}
        >
          {label}
          {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
        </Label>
        <div className="flex items-center gap-2">
          {characterCount && (
            <span
              className={cn(
                'text-xs',
                characterCount.current >= characterCount.max
                  ? 'text-orange-600'
                  : 'text-gray-500'
              )}
            >
              {characterCount.current}/{characterCount.max}
            </span>
          )}
          {showSuccess && (
            <Check
              className="h-4 w-4 text-green-500"
              aria-label="Valid"
            />
          )}
        </div>
      </div>

      {children}

      {showError && (
        <p
          className="text-sm text-red-600"
          role="alert"
          aria-live="polite"
        >
          {error}
        </p>
      )}
    </div>
  );
}
