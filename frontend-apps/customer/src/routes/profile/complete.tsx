import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ProfileWizard } from '@/components/profile';

export const Route = createFileRoute('/profile/complete')({
  component: ProfileCompletePage,
});

function ProfileCompletePage() {
  const navigate = useNavigate();

  // TODO: Get the actual customer ID from auth context
  const customerId = 'mock-customer-id';

  const handleComplete = () => {
    // Navigate to dashboard or home page
    navigate({ to: '/' });
  };

  const handleSkip = () => {
    // Navigate to dashboard or home page
    navigate({ to: '/' });
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <ProfileWizard
          customerId={customerId}
          onComplete={handleComplete}
          onSkip={handleSkip}
        />
      </div>
    </div>
  );
}
