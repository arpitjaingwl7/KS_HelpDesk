import { Alert, Snackbar } from '@mui/material';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { toastBus, type ToastMessage, type ToastSeverity } from './toastBus';
import { ToastContext, type ToastApi } from './useToast';

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<ToastMessage[]>([]);
  const [open, setOpen] = useState(false);
  // The id of the toast that has already been opened. Without it, closing a toast would immediately
  // reopen it: the message only leaves the queue after the exit transition (handleExited).
  const [shownId, setShownId] = useState<ToastMessage['id'] | null>(null);
  const current = queue[0];

  useEffect(
    () =>
      toastBus.subscribe((toast) => {
        setQueue((q) => {
          // Collapse identical consecutive messages (e.g. several 401s at once).
          const last = q[q.length - 1];
          if (last && last.message === toast.message && last.severity === toast.severity) return q;
          return [...q, toast];
        });
      }),
    [],
  );

  useEffect(() => {
    if (current && current.id !== shownId) {
      setShownId(current.id);
      setOpen(true);
    }
  }, [current, shownId]);

  const handleClose = (_event?: unknown, reason?: string) => {
    if (reason === 'clickaway') return;
    setOpen(false);
  };

  const handleExited = () => setQueue((q) => q.slice(1));

  const show = useCallback((message: string, severity: ToastSeverity = 'info') => {
    toastBus.emit(message, severity);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      success: (m) => show(m, 'success'),
      error: (m) => show(m, 'error'),
      info: (m) => show(m, 'info'),
      warning: (m) => show(m, 'warning'),
    }),
    [show],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Snackbar
        key={current?.id}
        open={open && Boolean(current)}
        autoHideDuration={current?.severity === 'error' ? 6000 : 4000}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        TransitionProps={{ onExited: handleExited }}
      >
        <Alert
          onClose={handleClose}
          severity={current?.severity ?? 'info'}
          variant="filled"
          sx={{ width: '100%', minWidth: 280 }}
        >
          {current?.message}
        </Alert>
      </Snackbar>
    </ToastContext.Provider>
  );
}
