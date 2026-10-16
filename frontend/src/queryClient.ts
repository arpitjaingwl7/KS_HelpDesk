import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { getProblem } from './api/problem';
import { toastBus } from './components/toastBus';

function onGlobalError(error: unknown) {
  if (getProblem(error)?.status === 403) {
    toastBus.emit("You don't have permission to do that.", 'warning');
  }
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({ onError: onGlobalError }),
    mutationCache: new MutationCache({ onError: onGlobalError }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          const status = getProblem(error)?.status ?? 0;
          // Only retry transient failures (network / 5xx), never 4xx.
          return (status === 0 || status >= 500) && failureCount < 2;
        },
      },
      mutations: { retry: false },
    },
  });
}
