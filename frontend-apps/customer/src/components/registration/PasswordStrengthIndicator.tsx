import { Check, X } from 'lucide-react';
import type { PasswordStrength } from '@/schemas/registration.schema';

interface PasswordStrengthIndicatorProps {
  strength: PasswordStrength;
  show: boolean;
}

export function PasswordStrengthIndicator({
  strength,
  show,
}: PasswordStrengthIndicatorProps) {
  if (!show) {
    return null;
  }

  const requirements = [
    { key: 'minLength', label: 'Minimum 8 characters', met: strength.requirements.minLength },
    { key: 'hasUppercase', label: 'At least one uppercase letter', met: strength.requirements.hasUppercase },
    { key: 'hasLowercase', label: 'At least one lowercase letter', met: strength.requirements.hasLowercase },
    { key: 'hasDigit', label: 'At least one digit', met: strength.requirements.hasDigit },
    { key: 'hasSpecialChar', label: 'At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)', met: strength.requirements.hasSpecialChar },
  ];

  return (
    <div className="mt-2 space-y-2">
      <div className="flex items-center gap-2">
        <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
          <div
            className={`h-full transition-all duration-200 ${strength.color}`}
            style={{ width: `${((strength.score + 1) / 4) * 100}%` }}
          />
        </div>
        <span
          className={`text-sm font-medium ${
            strength.label === 'Weak'
              ? 'text-red-600'
              : strength.label === 'Fair'
                ? 'text-orange-600'
                : strength.label === 'Good'
                  ? 'text-yellow-600'
                  : 'text-green-600'
          }`}
        >
          {strength.label}
        </span>
      </div>

      <ul className="space-y-1">
        {requirements.map(({ key, label, met }) => (
          <li key={key} className="flex items-center gap-2 text-sm">
            {met ? (
              <Check className="h-4 w-4 text-green-500" aria-hidden="true" />
            ) : (
              <X className="h-4 w-4 text-gray-400" aria-hidden="true" />
            )}
            <span className={met ? 'text-green-700' : 'text-gray-600'}>
              {label}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
