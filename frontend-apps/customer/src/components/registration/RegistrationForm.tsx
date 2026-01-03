import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';

import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

import { FormField } from './FormField';
import { PasswordInput } from './PasswordInput';
import { PasswordStrengthIndicator } from './PasswordStrengthIndicator';

import {
  registrationSchema,
  calculatePasswordStrength,
  type RegistrationFormData,
} from '@/schemas/registration.schema';

interface RegistrationFormProps {
  onSubmit: (data: RegistrationFormData) => Promise<void>;
}

export function RegistrationForm({ onSubmit }: RegistrationFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, touchedFields, isValid, dirtyFields },
    setValue,
    trigger,
  } = useForm<RegistrationFormData>({
    resolver: zodResolver(registrationSchema),
    mode: 'onBlur',
    defaultValues: {
      email: '',
      password: '',
      confirmPassword: '',
      firstName: '',
      lastName: '',
      tosAccepted: false,
      privacyPolicyAccepted: false,
      marketingOptIn: false,
    },
  });

  const password = watch('password');
  const firstName = watch('firstName');
  const lastName = watch('lastName');
  const passwordStrength = calculatePasswordStrength(password || '');

  const handleFormSubmit = async (data: RegistrationFormData) => {
    setIsSubmitting(true);
    try {
      await onSubmit(data);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleNameInput = (
    field: 'firstName' | 'lastName',
    value: string
  ) => {
    const truncated = value.slice(0, 50);
    setValue(field, truncated, { shouldValidate: true });
  };

  return (
    <Card className="w-full max-w-md mx-auto">
      <CardHeader>
        <CardTitle>Create Account</CardTitle>
        <CardDescription>
          Fill in your details to create a new account
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={handleSubmit(handleFormSubmit)}
          className="space-y-4"
          noValidate
        >
          <FormField
            label="Email"
            htmlFor="email"
            required
            error={errors.email?.message}
            touched={touchedFields.email}
            valid={dirtyFields.email && !errors.email}
          >
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
              autoComplete="email"
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              className={errors.email ? 'border-red-500' : ''}
              {...register('email')}
            />
          </FormField>

          <FormField
            label="Password"
            htmlFor="password"
            required
            error={errors.password?.message}
            touched={touchedFields.password}
            valid={dirtyFields.password && !errors.password}
          >
            <PasswordInput
              id="password"
              placeholder="Enter your password"
              autoComplete="new-password"
              aria-invalid={!!errors.password}
              aria-describedby="password-requirements"
              error={!!errors.password}
              {...register('password')}
            />
            <PasswordStrengthIndicator
              strength={passwordStrength}
              show={!!password && password.length > 0}
            />
          </FormField>

          <FormField
            label="Confirm Password"
            htmlFor="confirmPassword"
            required
            error={errors.confirmPassword?.message}
            touched={touchedFields.confirmPassword}
            valid={dirtyFields.confirmPassword && !errors.confirmPassword}
          >
            <PasswordInput
              id="confirmPassword"
              placeholder="Confirm your password"
              autoComplete="new-password"
              aria-invalid={!!errors.confirmPassword}
              error={!!errors.confirmPassword}
              {...register('confirmPassword')}
            />
          </FormField>

          <FormField
            label="First Name"
            htmlFor="firstName"
            required
            error={errors.firstName?.message}
            touched={touchedFields.firstName}
            valid={dirtyFields.firstName && !errors.firstName}
            characterCount={{ current: firstName?.length || 0, max: 50 }}
          >
            <Input
              id="firstName"
              type="text"
              placeholder="John"
              autoComplete="given-name"
              aria-invalid={!!errors.firstName}
              maxLength={50}
              className={errors.firstName ? 'border-red-500' : ''}
              {...register('firstName', {
                onChange: (e) => handleNameInput('firstName', e.target.value),
              })}
            />
          </FormField>

          <FormField
            label="Last Name"
            htmlFor="lastName"
            required
            error={errors.lastName?.message}
            touched={touchedFields.lastName}
            valid={dirtyFields.lastName && !errors.lastName}
            characterCount={{ current: lastName?.length || 0, max: 50 }}
          >
            <Input
              id="lastName"
              type="text"
              placeholder="Doe"
              autoComplete="family-name"
              aria-invalid={!!errors.lastName}
              maxLength={50}
              className={errors.lastName ? 'border-red-500' : ''}
              {...register('lastName', {
                onChange: (e) => handleNameInput('lastName', e.target.value),
              })}
            />
          </FormField>

          <div className="space-y-3 pt-2">
            <div className="flex items-start space-x-2">
              <Checkbox
                id="tosAccepted"
                aria-invalid={!!errors.tosAccepted}
                onCheckedChange={(checked) => {
                  setValue('tosAccepted', !!checked, {
                    shouldValidate: true,
                  });
                  trigger('tosAccepted');
                }}
                className={errors.tosAccepted ? 'border-red-500' : ''}
              />
              <div className="grid gap-1.5 leading-none">
                <Label
                  htmlFor="tosAccepted"
                  className={`text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${
                    errors.tosAccepted ? 'text-red-600' : ''
                  }`}
                >
                  I accept the Terms of Service{' '}
                  <span className="text-red-500" aria-hidden="true">*</span>
                </Label>
                {errors.tosAccepted && (
                  <p className="text-sm text-red-600" role="alert" aria-live="polite">
                    {errors.tosAccepted.message}
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-start space-x-2">
              <Checkbox
                id="privacyPolicyAccepted"
                aria-invalid={!!errors.privacyPolicyAccepted}
                onCheckedChange={(checked) => {
                  setValue('privacyPolicyAccepted', !!checked, {
                    shouldValidate: true,
                  });
                  trigger('privacyPolicyAccepted');
                }}
                className={errors.privacyPolicyAccepted ? 'border-red-500' : ''}
              />
              <div className="grid gap-1.5 leading-none">
                <Label
                  htmlFor="privacyPolicyAccepted"
                  className={`text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${
                    errors.privacyPolicyAccepted ? 'text-red-600' : ''
                  }`}
                >
                  I accept the Privacy Policy{' '}
                  <span className="text-red-500" aria-hidden="true">*</span>
                </Label>
                {errors.privacyPolicyAccepted && (
                  <p className="text-sm text-red-600" role="alert" aria-live="polite">
                    {errors.privacyPolicyAccepted.message}
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-start space-x-2">
              <Checkbox
                id="marketingOptIn"
                onCheckedChange={(checked) => {
                  setValue('marketingOptIn', !!checked, { shouldValidate: true });
                }}
              />
              <Label
                htmlFor="marketingOptIn"
                className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
              >
                I want to receive marketing emails (optional)
              </Label>
            </div>
          </div>

          <Button
            type="submit"
            className="w-full"
            disabled={!isValid || isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Creating Account...
              </>
            ) : (
              'Create Account'
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
