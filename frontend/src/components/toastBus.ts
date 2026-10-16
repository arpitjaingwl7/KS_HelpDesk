export type ToastSeverity = 'success' | 'info' | 'warning' | 'error';

export interface ToastMessage {
  id: number;
  message: string;
  severity: ToastSeverity;
}

type Listener = (toast: ToastMessage) => void;

const listeners = new Set<Listener>();
let nextId = 1;

/**
 * Tiny pub/sub so non-React code (axios interceptors, QueryClient caches)
 * can raise toasts that the SnackbarProvider renders.
 */
export const toastBus = {
  emit(message: string, severity: ToastSeverity = 'info'): void {
    const toast = { id: nextId++, message, severity };
    listeners.forEach((listener) => listener(toast));
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};
