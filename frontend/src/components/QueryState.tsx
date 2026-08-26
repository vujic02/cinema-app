import { errorMessage } from '../api/client';

/**
 * The two states every screen gained the moment its data started coming over a network, in one
 * place so they read the same everywhere.
 */

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p role="status" className="py-16 text-center text-sm text-muted">
      {label}
    </p>
  );
}

/**
 * A failed query, with the server's own explanation. `onRetry` gets a button — for a query it
 * is `refetch`; omit it where retrying makes no sense.
 */
export function ErrorNotice({
  error,
  fallback,
  onRetry
}: {
  error: unknown;
  fallback?: string;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-danger/40 bg-danger-surface px-4 py-3 text-sm text-danger"
    >
      <span>{errorMessage(error, fallback)}</span>
      {onRetry && (
        <button
          onClick={onRetry}
          className="rounded-lg border border-danger/50 px-3 py-1.5 text-xs font-semibold"
        >
          Try again
        </button>
      )}
    </div>
  );
}

/** An inline message that is not tied to a query — a rejected hold, a failed sign-in. */
export function InlineError({ message }: { message: string }) {
  return (
    <p role="alert" className="rounded-lg bg-danger-surface px-3 py-2 text-sm text-danger">
      {message}
    </p>
  );
}
