import { createContext, useContext } from 'react';
import type { ToastSeverity } from './toastBus';

export interface ToastApi {
  show(message: string, severity?: ToastSeverity): void;
  success(message: string): void;
  error(message: string): void;
  info(message: string): void;
  warning(message: string): void;
}

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <SnackbarProvider>');
  return ctx;
}
