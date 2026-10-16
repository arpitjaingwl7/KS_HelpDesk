import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Describes how a piece of UI state maps to and from URL search params.
 * `serialize` should omit default values so URLs stay short and shareable.
 */
export interface UrlStateCodec<T> {
  parse(params: URLSearchParams): T;
  serialize(state: T): URLSearchParams;
}

export type UrlStateUpdate<T> = Partial<T> | ((prev: T) => T);

export interface SetUrlStateOptions {
  /** Replace the current history entry instead of pushing a new one (default: false). */
  replace?: boolean;
}

/**
 * Keeps typed state in the URL query string. The URL is the single source of truth,
 * so state is bookmarkable, shareable and survives reloads.
 * Search params not managed by the codec are preserved.
 */
export function useUrlState<T>(
  codec: UrlStateCodec<T>,
  managedKeys: readonly string[],
): [T, (update: UrlStateUpdate<T>, options?: SetUrlStateOptions) => void] {
  const [searchParams, setSearchParams] = useSearchParams();

  const state = useMemo(() => codec.parse(searchParams), [codec, searchParams]);

  const setState = useCallback(
    (update: UrlStateUpdate<T>, options?: SetUrlStateOptions) => {
      setSearchParams(
        (prevParams) => {
          const prev = codec.parse(prevParams);
          const next = typeof update === 'function' ? update(prev) : { ...prev, ...update };
          const serialized = codec.serialize(next);
          const merged = new URLSearchParams();
          prevParams.forEach((value, key) => {
            if (!managedKeys.includes(key)) merged.append(key, value);
          });
          serialized.forEach((value, key) => merged.append(key, value));
          return merged;
        },
        { replace: options?.replace ?? false },
      );
    },
    [codec, managedKeys, setSearchParams],
  );

  return [state, setState];
}

// ---------- small parsing helpers shared by codecs ----------

export function parsePositiveInt(raw: string | null, fallback: number, max?: number): number {
  if (raw === null) return fallback;
  const n = Number.parseInt(raw, 10);
  if (!Number.isFinite(n) || n < 1) return fallback;
  return max !== undefined ? Math.min(n, max) : n;
}

/** Reads a multi-valued param, accepting both `a=X&a=Y` and `a=X,Y`; drops unknown values. */
export function parseEnumList<E extends string>(
  params: URLSearchParams,
  key: string,
  allowed: readonly E[],
): E[] {
  const values = params
    .getAll(key)
    .flatMap((v) => v.split(','))
    .map((v) => v.trim())
    .filter((v): v is E => (allowed as readonly string[]).includes(v));
  return Array.from(new Set(values));
}

export function parseEnum<E extends string>(
  raw: string | null,
  allowed: readonly E[],
): E | undefined {
  return raw !== null && (allowed as readonly string[]).includes(raw) ? (raw as E) : undefined;
}
