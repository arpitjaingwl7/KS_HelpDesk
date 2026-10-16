import { Alert, AlertTitle, Button, type AlertProps } from '@mui/material';
import { getProblem, problemMessage } from '../api/problem';

interface ErrorAlertProps extends Omit<AlertProps, 'children'> {
  error: unknown;
  title?: string;
  onRetry?: () => void;
}

/** Shows the `detail` of a ProblemDetail (or a generic message) in an MUI Alert. */
export function ErrorAlert({
  error,
  title,
  onRetry,
  severity = 'error',
  ...rest
}: ErrorAlertProps) {
  if (!error) return null;
  const problem = getProblem(error);
  return (
    <Alert
      severity={severity}
      action={
        onRetry ? (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        ) : undefined
      }
      {...rest}
    >
      {(title ?? problem?.title) && <AlertTitle>{title ?? problem?.title}</AlertTitle>}
      {problemMessage(error)}
      {problem?.traceId && (
        <span style={{ display: 'block', opacity: 0.6, fontSize: '0.75rem', marginTop: 4 }}>
          Trace ID: {problem.traceId}
        </span>
      )}
    </Alert>
  );
}
