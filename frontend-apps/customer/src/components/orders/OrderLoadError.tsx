import { Button } from "@/components/ui/button";

interface OrderLoadErrorProps {
  message: string;
  query: { refetch: () => unknown; isFetching: boolean };
}

/** An order read failed: the query doesn't retry by itself, so the customer can. */
export function OrderLoadError({ message, query }: OrderLoadErrorProps) {
  return (
    <div role="alert" className="text-center text-red-400">
      <p>{message}</p>
      <Button
        variant="outline"
        className="mt-4"
        disabled={query.isFetching}
        onClick={() => query.refetch()}
      >
        Try again
      </Button>
    </div>
  );
}
