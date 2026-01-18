import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Link } from "@tanstack/react-router";

import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

import { FormField } from "@/components/registration/FormField";
import { PasswordInput } from "@/components/registration/PasswordInput";

import { signinSchema, type SigninFormData } from "@/schemas/signin.schema";

export interface SigninFormProps {
  onSubmit: (data: SigninFormData) => Promise<void>;
  error?: string;
}

export function SigninForm({ onSubmit, error }: SigninFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, touchedFields, isValid, dirtyFields },
    setValue,
    trigger,
  } = useForm<SigninFormData>({
    resolver: zodResolver(signinSchema),
    mode: "onBlur",
    defaultValues: {
      email: "",
      password: "",
      rememberMe: false,
    },
  });

  const handleFormSubmit = async (data: SigninFormData) => {
    setIsSubmitting(true);
    try {
      await onSubmit(data);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="w-full max-w-md mx-auto">
      <CardHeader>
        <CardTitle>Sign In</CardTitle>
        <CardDescription>
          Enter your credentials to access your account
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={handleSubmit(handleFormSubmit)}
          className="space-y-4"
          noValidate
        >
          {error && (
            <div
              className="p-3 text-sm text-red-600 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-md"
              role="alert"
              aria-live="polite"
            >
              {error}
            </div>
          )}

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
              aria-describedby={errors.email ? "email-error" : undefined}
              className={errors.email ? "border-red-500" : ""}
              {...register("email")}
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
              autoComplete="current-password"
              aria-invalid={!!errors.password}
              error={!!errors.password}
              {...register("password")}
            />
          </FormField>

          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Checkbox
                id="rememberMe"
                onCheckedChange={(checked) => {
                  setValue("rememberMe", !!checked, {
                    shouldValidate: true,
                  });
                  trigger("rememberMe");
                }}
              />
              <Label
                htmlFor="rememberMe"
                className="text-sm font-medium leading-none cursor-pointer"
              >
                Remember me
              </Label>
            </div>
            <Link
              to="/forgot-password"
              className="text-sm text-primary hover:underline"
            >
              Forgot password?
            </Link>
          </div>

          <Button
            type="submit"
            className="w-full"
            disabled={!isValid || isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Signing in...
              </>
            ) : (
              "Sign In"
            )}
          </Button>
        </form>
      </CardContent>
      <CardFooter className="flex justify-center">
        <p className="text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link to="/registration" className="text-primary hover:underline">
            Create one
          </Link>
        </p>
      </CardFooter>
    </Card>
  );
}
